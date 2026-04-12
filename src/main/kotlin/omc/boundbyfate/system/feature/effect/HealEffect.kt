package omc.boundbyfate.system.feature.effect

import omc.boundbyfate.api.dice.DiceRoller
import omc.boundbyfate.api.dice.DiceType
import omc.boundbyfate.api.feature.FeatureContext
import omc.boundbyfate.api.feature.FeatureEffect
import omc.boundbyfate.registry.BbfStats

/**
 * Heals each target for diceCount * diceType + bonus.
 *
 * JSON params:
 * - diceCount: Int (default 1)
 * - diceType: String (D4/D6/D8/D10/D12, default D8)
 * - bonusStat: String? (stat short name like "CON" - adds dnd modifier)
 * - bonusFlat: Int (flat bonus, default 0)
 * - bonusLevel: Boolean (add caster level as bonus, default false)
 */
class HealEffect(
    private val diceCount: Int = 1,
    private val diceType: DiceType = DiceType.D8,
    private val bonusStatId: net.minecraft.util.Identifier? = null,
    private val bonusFlat: Int = 0,
    private val bonusLevel: Boolean = false
) : FeatureEffect {

    override fun apply(context: FeatureContext) {
        var bonus = bonusFlat
        if (bonusLevel) bonus += context.casterLevel
        if (bonusStatId != null) {
            bonus += context.casterStats?.getStatValue(bonusStatId)?.dndModifier ?: 0
        }

        val roll = DiceRoller.roll(diceCount, diceType, bonus)

        for (target in context.targets) {
            val newHealth = (target.health + roll.total).coerceAtMost(target.maxHealth)
            target.health = newHealth
        }
    }
}
