package omc.boundbyfate.system.ability.effect

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import omc.boundbyfate.api.ability.AbilityContext
import omc.boundbyfate.api.ability.AbilityEffect
import org.slf4j.LoggerFactory

/**
 * Эффект телепортации.
 * 
 * Телепортирует кастера или цель.
 * Поддерживает разные режимы телепортации.
 * 
 * JSON параметры:
 * ```json
 * {
 *   "type": "boundbyfate-core:teleport",
 *   "mode": "TO_CURSOR",
 *   "maxDistance": 30,
 *   "requiresSafeLanding": true
 * }
 * ```
 */
class TeleportEffect(
    /** Режим телепортации */
    val mode: TeleportMode = TeleportMode.TO_CURSOR,
    
    /** Максимальная дистанция телепортации */
    val maxDistance: Float = 30f,
    
    /** Требуется ли безопасное место для приземления */
    val requiresSafeLanding: Boolean = true
) : AbilityEffect {
    
    override val type = Identifier("boundbyfate-core", "teleport")
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    override fun apply(context: AbilityContext): Boolean {
        val caster = context.caster
        
        // Определяем точку телепортации
        val targetPos = when (mode) {
            TeleportMode.TO_CURSOR -> {
                // Телепорт куда смотрит игрок
                if (caster is ServerPlayerEntity) {
                    val raycast = caster.raycast(maxDistance.toDouble(), 0f, false)
                    if (raycast.type == HitResult.Type.BLOCK) {
                        raycast.pos
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            
            TeleportMode.BEHIND_TARGET -> {
                // Телепорт за спину цели
                val target = context.getTarget()
                if (target != null) {
                    val direction = target.rotationVector.multiply(-1.0)
                    target.pos.add(direction.multiply(2.0))
                } else {
                    null
                }
            }
            
            TeleportMode.SWAP_WITH_TARGET -> {
                // Обмен местами с целью
                val target = context.getTarget()
                if (target != null) {
                    val casterPos = caster.pos
                    val targetPos = target.pos
                    
                    // Телепортируем цель на место кастера
                    target.teleport(casterPos.x, casterPos.y, casterPos.z)
                    
                    // Возвращаем позицию цели для телепортации кастера
                    targetPos
                } else {
                    null
                }
            }
        }
        
        if (targetPos == null) {
            logger.warn("TeleportEffect: failed to determine target position")
            return false
        }
        
        // Проверяем дистанцию
        val distance = caster.pos.distanceTo(targetPos)
        if (distance > maxDistance) {
            logger.debug("TeleportEffect: target too far (${distance} > ${maxDistance})")
            return false
        }
        
        // Проверяем безопасность места
        if (requiresSafeLanding) {
            // TODO: Проверить, что место безопасно (нет лавы, пропасти, etc.)
        }
        
        // Телепортируем
        caster.teleport(targetPos.x, targetPos.y, targetPos.z)
        
        logger.debug("TeleportEffect: teleported ${caster.name.string} to $targetPos")
        return true
    }
}

enum class TeleportMode {
    /** Куда смотрит игрок */
    TO_CURSOR,
    
    /** За спину цели */
    BEHIND_TARGET,
    
    /** Обмен местами с целью */
    SWAP_WITH_TARGET
}
