package omc.boundbyfate.system.ability.effect

import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier
import omc.boundbyfate.api.ability.AbilityContext
import omc.boundbyfate.api.ability.AbilityEffect
import omc.boundbyfate.api.damage.BbfDamage
import omc.boundbyfate.api.dice.DiceRoller
import omc.boundbyfate.api.dice.DiceType
import org.slf4j.LoggerFactory

/**
 * Эффект нанесения урона.
 * 
 * Наносит урон всем целям в контексте.
 * Поддерживает:
 * - Броски костей
 * - Бонусы от характеристик
 * - Бонусы от уровня
 * - Плоские бонусы
 * - Сохранение урона в контексте для других эффектов
 * 
 * JSON параметры:
 * ```json
 * {
 *   "type": "boundbyfate-core:damage",
 *   "dice": { "count": 8, "type": "D6" },
 *   "damageType": "boundbyfate-core:fire",
 *   "bonusStat": "boundbyfate-core:intelligence",
 *   "bonusFlat": 5,
 *   "bonusLevel": true
 * }
 * ```
 */
class DamageEffect(
    /** Выражение для броска костей */
    val dice: DiceExpression,
    
    /** Тип урона */
    val damageType: Identifier,
    
    /** ID характеристики для бонуса (добавляет D&D модификатор) */
    val bonusStat: Identifier? = null,
    
    /** Плоский бонус к урону */
    val bonusFlat: Int = 0,
    
    /** Добавлять ли уровень персонажа как бонус */
    val bonusLevel: Boolean = false,
    
    /** Игнорировать ли броню (для force damage) */
    val ignoresArmor: Boolean = false
) : AbilityEffect {
    
    override val type = Identifier("boundbyfate-core", "damage")
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    override fun apply(context: AbilityContext): Boolean {
        if (!context.hasTargets()) {
            logger.warn("DamageEffect: no targets in context")
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
        val totalDamage = roll.total.toFloat()
        
        // Сохраняем урон в контексте для других эффектов (например, lifesteal)
        context.data["last_damage"] = totalDamage
        context.data["base_damage"] = totalDamage
        
        // Создаём источник урона
        val damageSource = BbfDamage.of(
            context.world,
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, damageType),
            context.caster
        )
        
        // Наносим урон всем целям
        var anySuccess = false
        for (target in context.targets) {
            // Проверяем спасбросок (если был)
            val actualDamage = if (context.savingThrowResults.containsKey(target.uuid)) {
                if (context.didTargetSave(target)) {
                    // Цель прошла спасбросок - половина урона
                    totalDamage / 2f
                } else {
                    totalDamage
                }
            } else {
                totalDamage
            }
            
            // Наносим урон
            val damaged = target.damage(damageSource, actualDamage)
            if (damaged) {
                anySuccess = true
                logger.debug(
                    "DamageEffect: dealt ${actualDamage} ${damageType.path} damage to ${target.name.string}"
                )
            }
        }
        
        return anySuccess
    }
}

/**
 * Выражение для броска костей.
 */
data class DiceExpression(
    val count: Int = 1,
    val type: DiceType = DiceType.D6
)
