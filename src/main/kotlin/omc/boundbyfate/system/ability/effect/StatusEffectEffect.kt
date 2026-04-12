package omc.boundbyfate.system.ability.effect

import net.minecraft.entity.effect.StatusEffect
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import omc.boundbyfate.api.ability.AbilityContext
import omc.boundbyfate.api.ability.AbilityEffect
import org.slf4j.LoggerFactory

/**
 * Эффект наложения статусного эффекта.
 * 
 * Накладывает статусный эффект (potion effect) на цели.
 * 
 * JSON параметры:
 * ```json
 * {
 *   "type": "boundbyfate-core:status_effect",
 *   "effectId": "minecraft:poison",
 *   "duration": 200,
 *   "amplifier": 0,
 *   "ambient": false,
 *   "showParticles": true,
 *   "showIcon": true
 * }
 * ```
 */
class StatusEffectEffect(
    /** ID статусного эффекта */
    val effectId: Identifier,
    
    /** Длительность в тиках */
    val duration: Int = 200,
    
    /** Усилитель эффекта (0 = уровень 1) */
    val amplifier: Int = 0,
    
    /** Ambient эффект (менее заметные партиклы) */
    val ambient: Boolean = false,
    
    /** Показывать ли партиклы */
    val showParticles: Boolean = true,
    
    /** Показывать ли иконку в инвентаре */
    val showIcon: Boolean = true
) : AbilityEffect {
    
    override val type = Identifier("boundbyfate-core", "status_effect")
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    override fun canApply(context: AbilityContext): Boolean {
        // Проверяем, что эффект существует
        return Registries.STATUS_EFFECT.containsId(effectId)
    }
    
    override fun apply(context: AbilityContext): Boolean {
        if (!context.hasTargets()) {
            logger.warn("StatusEffectEffect: no targets in context")
            return false
        }
        
        // Получаем статусный эффект
        val statusEffect = Registries.STATUS_EFFECT.get(effectId)
        if (statusEffect == null) {
            logger.error("StatusEffectEffect: unknown status effect $effectId")
            return false
        }
        
        // Создаём instance эффекта
        val effectInstance = StatusEffectInstance(
            statusEffect,
            duration,
            amplifier,
            ambient,
            showParticles,
            showIcon
        )
        
        // Накладываем на все цели
        var anySuccess = false
        for (target in context.targets) {
            // Проверяем спасбросок (если был)
            if (context.savingThrowResults.containsKey(target.uuid)) {
                if (context.didTargetSave(target)) {
                    // Цель прошла спасбросок - не накладываем эффект
                    logger.debug("StatusEffectEffect: ${target.name.string} saved against $effectId")
                    continue
                }
            }
            
            val added = target.addStatusEffect(effectInstance)
            if (added) {
                anySuccess = true
                logger.debug(
                    "StatusEffectEffect: applied $effectId to ${target.name.string} " +
                    "for ${duration} ticks (amplifier: $amplifier)"
                )
            }
        }
        
        return anySuccess
    }
}
