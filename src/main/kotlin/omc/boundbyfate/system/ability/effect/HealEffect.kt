package omc.boundbyfate.system.ability.effect

import net.minecraft.util.Identifier
import omc.boundbyfate.api.ability.AbilityContext
import omc.boundbyfate.api.ability.AbilityEffect
import omc.boundbyfate.api.dice.DiceRoller
import org.slf4j.LoggerFactory

/**
 * Эффект исцеления.
 * 
 * Восстанавливает здоровье целям.
 * Поддерживает:
 * - Броски костей
 * - Бонусы от характеристик
 * - Бонусы от уровня
 * - Плоские бонусы
 * - Избыточное исцеление как временные HP
 * 
 * JSON параметры:
 * ```json
 * {
 *   "type": "boundbyfate-core:heal",
 *   "dice": { "count": 1, "type": "D10" },
 *   "bonusStat": "boundbyfate-core:wisdom",
 *   "bonusFlat": 0,
 *   "bonusLevel": true,
 *   "canOverheal": false,
 *   "overhealAsTemp": true
 * }
 * ```
 */
class HealEffect(
    /** Выражение для броска костей */
    val dice: DiceExpression,
    
    /** ID характеристики для бонуса */
    val bonusStat: Identifier? = null,
    
    /** Плоский бонус к исцелению */
    val bonusFlat: Int = 0,
    
    /** Добавлять ли уровень персонажа как бонус */
    val bonusLevel: Boolean = false,
    
    /** Может ли исцеление превышать максимальное HP */
    val canOverheal: Boolean = false,
    
    /** Превращать ли избыточное исцеление во временные HP */
    val overhealAsTemp: Boolean = false,
    
    /** Может ли воскрешать мёртвых (для Revivify) */
    val reviveDead: Boolean = false
) : AbilityEffect {
    
    override val type = Identifier("boundbyfate-core", "heal")
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    override fun apply(context: AbilityContext): Boolean {
        if (!context.hasTargets()) {
            logger.warn("HealEffect: no targets in context")
            return false
        }
        
        // Вычисляем бонус
        var bonus = bonusFlat
        
        if (bonusLevel) {
            bonus += context.casterLevel
        }
        
        if (bonusStat != null && context.casterStats != null) {
            bonus += context.casterStats.getStatValue(bonusStat).dndModifier
        }
        
        // Бросаем кости
        val roll = DiceRoller.roll(dice.count, dice.type, bonus)
        val totalHeal = roll.total.toFloat()
        
        // Исцеляем все цели
        var anySuccess = false
        for (target in context.targets) {
            // Пропускаем мёртвых (если не reviveDead)
            if (!target.isAlive && !reviveDead) {
                continue
            }
            
            val currentHealth = target.health
            val maxHealth = target.maxHealth
            val newHealth = currentHealth + totalHeal
            
            if (canOverheal) {
                // Исцеление может превышать максимум
                target.health = newHealth
                anySuccess = true
            } else if (overhealAsTemp && newHealth > maxHealth) {
                // Избыточное исцеление → временные HP
                target.health = maxHealth
                val overheal = newHealth - maxHealth
                // TODO: Добавить временные HP через absorption effect
                logger.debug("HealEffect: ${target.name.string} gained ${overheal} temp HP")
                anySuccess = true
            } else {
                // Обычное исцеление с ограничением по максимуму
                target.health = newHealth.coerceAtMost(maxHealth)
                anySuccess = true
            }
            
            logger.debug(
                "HealEffect: healed ${target.name.string} for ${totalHeal} HP " +
                "(${currentHealth} -> ${target.health})"
            )
        }
        
        return anySuccess
    }
}
