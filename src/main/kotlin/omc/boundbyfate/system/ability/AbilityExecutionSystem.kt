package omc.boundbyfate.system.ability

import net.minecraft.entity.LivingEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import omc.boundbyfate.api.ability.AbilityContext
import omc.boundbyfate.api.ability.AbilityDefinition
import omc.boundbyfate.api.ability.AbilityPhase
import omc.boundbyfate.api.ability.component.MetadataComponent
import omc.boundbyfate.api.ability.component.ScalingComponent
import omc.boundbyfate.api.ability.component.TargetingComponent
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.network.ServerPacketHandler
import org.slf4j.LoggerFactory

/**
 * Система выполнения способностей.
 * 
 * Управляет фазовым выполнением способностей:
 * 1. PREPARATION - подготовка (проверка условий, вычисление целей)
 * 2. CAST - каст (визуальные эффекты, звуки, создание проджектайлов)
 * 3. APPLICATION - применение (урон, исцеление, статусные эффекты)
 * 4. POST_APPLICATION - пост-обработка (концентрация, дополнительные эффекты)
 */
object AbilityExecutionSystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")
    
    /**
     * Выполняет способность.
     * 
     * @param caster Кастер способности
     * @param ability Определение способности
     * @param target Первичная цель (опционально)
     * @param targetPos Позиция цели (опционально)
     * @param upcastLevel Уровень апкаста (опционально)
     * @return true если способность успешно выполнена
     */
    fun execute(
        caster: LivingEntity,
        ability: AbilityDefinition,
        target: LivingEntity? = null,
        targetPos: Vec3d? = null,
        upcastLevel: Int? = null
    ): Boolean {
        if (caster.world !is ServerWorld) {
            logger.error("Cannot execute ability on client side")
            return false
        }
        
        val world = caster.world as ServerWorld
        
        // Получаем статы кастера
        val casterStats = caster.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
        val casterLevel = if (caster is ServerPlayerEntity) {
            caster.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, null)?.level ?: 1
        } else {
            1
        }
        
        // Разрешаем цели
        val targets = resolveTargets(caster, ability.targeting, target, targetPos, world)
        
        // Создаём базовый контекст
        var context = AbilityContext(
            caster = caster,
            targets = targets,
            targetPos = targetPos,
            world = world,
            abilityId = ability.id,
            phase = AbilityPhase.PREPARATION,
            upcastLevel = upcastLevel,
            casterLevel = casterLevel,
            casterStats = casterStats
        )
        
        // Применяем масштабирование
        context = applyScaling(context, ability)
        
        // Выполняем спасброски (если нужны)
        context = performSavingThrows(context, ability)
        
        // Выполняем все 4 фазы
        for (phase in AbilityPhase.entries) {
            context = context.copy(phase = phase)
            
            if (!ability.executePhase(context)) {
                logger.debug("Ability ${ability.id} failed at phase $phase")
                return false
            }
        }
        
        // Обрабатываем концентрацию
        handleConcentration(caster, ability)
        
        // Broadcast для визуальных эффектов
        if (caster is ServerPlayerEntity) {
            ServerPacketHandler.broadcastAbilityCast(caster, ability.id, world)
        }
        
        logger.debug("${caster.name?.string ?: "Entity"} executed ${ability.id}")
        return true
    }
    
    // ═══ PRIVATE HELPERS ═══
    
    /**
     * Разрешает цели на основе компонента таргетинга.
     */
    private fun resolveTargets(
        caster: LivingEntity,
        targeting: TargetingComponent,
        primaryTarget: LivingEntity?,
        targetPos: Vec3d?,
        world: ServerWorld
    ): List<LivingEntity> {
        return when (targeting) {
            is TargetingComponent.Self -> {
                listOf(caster)
            }
            
            is TargetingComponent.SingleTarget -> {
                if (primaryTarget != null) {
                    listOf(primaryTarget)
                } else {
                    emptyList()
                }
            }
            
            is TargetingComponent.Projectile -> {
                // Проджектайлы разрешают цели при попадании
                // Здесь возвращаем пустой список, цели будут добавлены позже
                emptyList()
            }
            
            is TargetingComponent.Area -> {
                if (targetPos == null) {
                    emptyList()
                } else {
                    val box = Box.of(targetPos, targeting.radius * 2, targeting.radius * 2, targeting.radius * 2)
                    world.getEntitiesByClass(LivingEntity::class.java, box) { entity ->
                        entity != caster && entity.pos.distanceTo(targetPos) <= targeting.radius
                    }
                }
            }
            
            is TargetingComponent.Zone -> {
                // Зоны создают persistent entity, цели разрешаются каждый тик
                emptyList()
            }
            
            is TargetingComponent.Cone -> {
                val casterPos = caster.pos
                val lookVec = caster.rotationVector
                val halfAngle = Math.toRadians(targeting.angle / 2.0)
                
                val box = Box.of(casterPos, targeting.range * 2, targeting.range * 2, targeting.range * 2)
                world.getEntitiesByClass(LivingEntity::class.java, box) { entity ->
                    if (entity == caster) return@getEntitiesByClass false
                    
                    val toEntity = entity.pos.subtract(casterPos).normalize()
                    val distance = entity.pos.distanceTo(casterPos)
                    
                    if (distance > targeting.range) return@getEntitiesByClass false
                    
                    val angle = Math.acos(lookVec.dotProduct(toEntity))
                    angle <= halfAngle
                }
            }
            
            is TargetingComponent.Line -> {
                val casterPos = caster.pos
                val lookVec = caster.rotationVector
                val endPos = casterPos.add(lookVec.multiply(targeting.range.toDouble()))
                
                val box = Box.of(casterPos, targeting.range * 2, targeting.range * 2, targeting.range * 2)
                world.getEntitiesByClass(LivingEntity::class.java, box) { entity ->
                    if (entity == caster) return@getEntitiesByClass false
                    
                    val distance = distanceToLineSegment(entity.pos, casterPos, endPos)
                    distance <= targeting.width
                }
            }
        }
    }
    
    /**
     * Применяет масштабирование к контексту.
     */
    private fun applyScaling(context: AbilityContext, ability: AbilityDefinition): AbilityContext {
        var modifiedContext = context
        
        for (scaling in ability.scaling) {
            when (scaling) {
                is ScalingComponent.Upcast -> {
                    val upcastLevel = context.upcastLevel
                    if (upcastLevel != null) {
                        val baseLevel = ability.getMetadata<MetadataComponent.Spell>()?.level ?: 1
                        val levelsAboveBase = upcastLevel - baseLevel
                        
                        if (levelsAboveBase > 0) {
                            // Сохраняем информацию о масштабировании в data
                            modifiedContext.data["upcast_levels"] = levelsAboveBase
                            modifiedContext.data["upcast_per_level"] = scaling.perLevel
                        }
                    }
                }
                
                is ScalingComponent.CharacterLevel -> {
                    val level = context.casterLevel
                    val scalingValue = when {
                        level >= 17 -> scaling.at17
                        level >= 11 -> scaling.at11
                        level >= 5 -> scaling.at5
                        else -> 0
                    }
                    
                    modifiedContext.data["character_level_scaling"] = scalingValue
                }
            }
        }
        
        return modifiedContext
    }
    
    /**
     * Выполняет спасброски для всех целей.
     */
    private fun performSavingThrows(context: AbilityContext, ability: AbilityDefinition): AbilityContext {
        val savingThrow = ability.getMetadata<MetadataComponent.SavingThrow>()
            ?: return context
        
        val dc = SavingThrowSystem.calculateSpellSaveDC(context.caster, context.casterStats)
        val results = mutableMapOf<java.util.UUID, Boolean>()
        
        for (target in context.targets) {
            val success = SavingThrowSystem.makeSave(target, savingThrow.ability, dc)
            results[target.uuid] = success
        }
        
        return context.copy(savingThrowResults = results)
    }
    
    /**
     * Обрабатывает концентрацию для заклинаний.
     */
    private fun handleConcentration(caster: LivingEntity, ability: AbilityDefinition) {
        val spell = ability.getMetadata<MetadataComponent.Spell>() ?: return
        
        if (spell.requiresConcentration && caster is ServerPlayerEntity) {
            ConcentrationSystem.startConcentration(caster, ability.id)
        }
    }
    
    /**
     * Вычисляет расстояние от точки до отрезка.
     */
    private fun distanceToLineSegment(point: Vec3d, lineStart: Vec3d, lineEnd: Vec3d): Double {
        val lineVec = lineEnd.subtract(lineStart)
        val pointVec = point.subtract(lineStart)
        
        val lineLength = lineVec.length()
        if (lineLength == 0.0) return point.distanceTo(lineStart)
        
        val t = (pointVec.dotProduct(lineVec) / (lineLength * lineLength)).coerceIn(0.0, 1.0)
        val projection = lineStart.add(lineVec.multiply(t))
        
        return point.distanceTo(projection)
    }
}
