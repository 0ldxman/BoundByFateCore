package omc.boundbyfate.system.effect

import net.minecraft.util.Identifier
import omc.boundbyfate.api.dice.DiceRoller
import omc.boundbyfate.api.dice.DiceType
import omc.boundbyfate.api.effect.BbfEffect
import omc.boundbyfate.api.effect.BbfEffectContext
import org.slf4j.LoggerFactory

/**
 * Heals each target for diceCount * diceType + bonus.
 *
 * JSON params:
 * - diceCount: Int (default 1)
 * - diceType: String (D4/D6/D8/D10/D12, default D8)
 * - bonusStat: String? (stat identifier — adds D&D modifier)
 * - bonusFlat: Int (flat bonus, default 0)
 * - bonusLevel: Boolean (add source entity level as bonus, default false)
 * - overhealAsTemp: Boolean (excess healing becomes absorption/temp HP, default false)
 */
class HealEffect(
    private val diceCount: Int = 1,
    private val diceType: DiceType = DiceType.D8,
    private val bonusStatId: Identifier? = null,
    private val bonusFlat: Int = 0,
    private val bonusLevel: Boolean = false,
    private val overhealAsTemp: Boolean = false
) : BbfEffect {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun apply(context: BbfEffectContext): Boolean {
        var bonus = bonusFlat
        if (bonusLevel) bonus += context.sourceLevel
        if (bonusStatId != null) {
            bonus += context.sourceStats?.getStatValue(bonusStatId)?.dndModifier ?: 0
        }

        val roll = DiceRoller.roll(diceCount, diceType, bonus)
        val amount = roll.total.toFloat()

        for (target in context.targets) {
            val overflow = (target.health + amount) - target.maxHealth
            if (overhealAsTemp && overflow > 0) {
                target.health = target.maxHealth
                target.absorptionAmount = (target.absorptionAmount + overflow)
            } else {
                target.health = (target.health + amount).coerceAtMost(target.maxHealth)
            }
            logger.debug("HealEffect: healed ${target.name.string} for $amount")
        }
        return true
    }
}
