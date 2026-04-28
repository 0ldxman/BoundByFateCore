package omc.boundbyfate.system.feature

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.api.effect.EffectContext
import omc.boundbyfate.api.feature.FeatureGrant
import omc.boundbyfate.component.core.getOrCreate
import omc.boundbyfate.registry.EffectRegistry
import omc.boundbyfate.system.effect.EffectApplier
import omc.boundbyfate.system.mechanic.ClassMechanicManager
import omc.boundbyfate.util.source.SourceReference
import org.slf4j.LoggerFactory

/**
 * Система управления особенностями классов.
 *
 * Отвечает за:
 * - Применение грантов особенности
 * - Снятие грантов особенности
 * - Получение информации об особенностях персонажа
 *
 * ## Использование
 *
 * ```kotlin
 * // Применить особенность
 * FeatureSystem.applyFeature(player, featureId, sourceClassId, level)
 *
 * // Снять особенность
 * FeatureSystem.removeFeature(player, featureId)
 * ```
 */
object FeatureSystem {
    
    private val logger = LoggerFactory.getLogger(FeatureSystem::class.java)
    
    /**
     * Применяет особенность к игроку.
     *
     * @param player игрок
     * @param featureId ID особенности
     * @param sourceClassId ID класса который даёт особенность
     * @param level уровень на котором даётся особенность
     */
    fun applyFeature(
        player: ServerPlayerEntity,
        featureId: Identifier,
        sourceClassId: Identifier,
        level: Int
    ) {
        val feature = FeatureRegistry.get(featureId)
        if (feature == null) {
            logger.error("Feature $featureId not found in registry")
            return
        }
        
        logger.info("Applying feature ${feature.id} to ${player.name.string} (from $sourceClassId level $level)")
        
        // Применяем все гранты особенности
        for (grant in feature.grants) {
            applyGrant(player, grant, feature.id, sourceClassId, level)
        }
    }
    
    /**
     * Применяет один грант особенности.
     */
    private fun applyGrant(
        player: ServerPlayerEntity,
        grant: FeatureGrant,
        featureId: Identifier,
        sourceClassId: Identifier,
        level: Int
    ) {
        when (grant) {
            is FeatureGrant.Effect -> {
                val handler = EffectRegistry.getHandler(grant.definition.id) ?: run {
                    logger.warn("Effect handler '${grant.definition.id}' not found for feature $featureId")
                    return
                }
                val source = SourceReference.feature(featureId)
                val ctx = EffectContext.passive(player, grant.definition, source)
                EffectApplier.apply(handler, ctx)
                logger.debug("Applied effect '${grant.definition.id}' from feature $featureId")
            }
            
            is FeatureGrant.Ability -> {
                val stats = player.getOrCreate(omc.boundbyfate.component.components.EntityAbilitiesData.TYPE)
                if (!stats.knownAbilities.contains(grant.abilityId)) {
                    stats.knownAbilities.add(grant.abilityId)
                }
                logger.debug("Granted ability ${grant.abilityId} from feature $featureId")
            }
            
            is FeatureGrant.Resource -> {
                val abilities = player.getOrCreate(omc.boundbyfate.component.components.EntityAbilitiesData.TYPE)
                val current = abilities.resourcesMaximums[grant.resourceId] ?: 0
                abilities.resourcesMaximums[grant.resourceId] = current + grant.amount
                // Инициализируем текущее значение если ещё нет
                if (!abilities.resourcesCurrent.containsKey(grant.resourceId)) {
                    abilities.resourcesCurrent[grant.resourceId] = current + grant.amount
                }
                logger.debug("Granted resource ${grant.resourceId} (${grant.amount}) from feature $featureId")
            }
            
            is FeatureGrant.Mechanic -> {
                // Активируем механику через ClassMechanicManager
                ClassMechanicManager.activateMechanic(
                    player = player,
                    mechanicId = grant.mechanicId,
                    config = grant.config,
                    sourceFeature = featureId,
                    sourceClass = sourceClassId
                )
            }
            
            is FeatureGrant.Proficiency -> {
                omc.boundbyfate.system.proficiency.ProficiencySystem.addProficiency(player, grant.proficiencyId)
                logger.debug("Granted proficiency ${grant.proficiencyId} from feature $featureId")
            }
        }
    }
    
    /**
     * Снимает особенность с игрока.
     *
     * @param player игрок
     * @param featureId ID особенности
     */
    fun removeFeature(player: ServerPlayerEntity, featureId: Identifier) {
        val feature = FeatureRegistry.get(featureId)
        if (feature == null) {
            logger.error("Feature $featureId not found in registry")
            return
        }
        
        logger.info("Removing feature ${feature.id} from ${player.name.string}")
        
        // Снимаем все гранты особенности
        for (grant in feature.grants) {
            removeGrant(player, grant, feature.id)
        }
    }
    
    /**
     * Снимает один грант особенности.
     */
    private fun removeGrant(
        player: ServerPlayerEntity,
        grant: FeatureGrant,
        featureId: Identifier
    ) {
        when (grant) {
            is FeatureGrant.Effect -> {
                val handler = EffectRegistry.getHandler(grant.definition.id) ?: run {
                    logger.warn("Effect handler '${grant.definition.id}' not found for feature $featureId")
                    return
                }
                val source = SourceReference.feature(featureId)
                val ctx = EffectContext.passive(player, grant.definition, source)
                EffectApplier.remove(handler, ctx)
                logger.debug("Removed effect '${grant.definition.id}' from feature $featureId")
            }
            
            is FeatureGrant.Ability -> {
                player.getOrCreate(omc.boundbyfate.component.components.EntityAbilitiesData.TYPE)
                    .knownAbilities.remove(grant.abilityId)
                logger.debug("Removed ability ${grant.abilityId} from feature $featureId")
            }
            
            is FeatureGrant.Resource -> {
                val abilities = player.getOrCreate(omc.boundbyfate.component.components.EntityAbilitiesData.TYPE)
                val current = abilities.resourcesMaximums[grant.resourceId] ?: 0
                val newMax = maxOf(0, current - grant.amount)
                if (newMax == 0) {
                    abilities.resourcesMaximums.remove(grant.resourceId)
                    abilities.resourcesCurrent.remove(grant.resourceId)
                } else {
                    abilities.resourcesMaximums[grant.resourceId] = newMax
                    // Ограничиваем текущее значение новым максимумом
                    val currentVal = abilities.resourcesCurrent[grant.resourceId] ?: 0
                    abilities.resourcesCurrent[grant.resourceId] = minOf(currentVal, newMax)
                }
                logger.debug("Removed resource ${grant.resourceId} from feature $featureId")
            }
            
            is FeatureGrant.Mechanic -> {
                ClassMechanicManager.deactivateMechanic(player, grant.mechanicId)
            }
            
            is FeatureGrant.Proficiency -> {
                omc.boundbyfate.system.proficiency.ProficiencySystem.removeProficiency(player, grant.proficiencyId)
                logger.debug("Removed proficiency ${grant.proficiencyId} from feature $featureId")
            }
        }
    }
}
