package omc.boundbyfate.system.effect

import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier
import omc.boundbyfate.api.damage.BbfDamage
import omc.boundbyfate.api.dice.DiceRoller
import omc.boundbyfate.api.dice.DiceType
import omc.boundbyfate.api.effect.BbfEffect
import omc.boundbyfate.api.effect.BbfEffectContext
import org.slf4j.LoggerFactory

/**
 * Deals damage to each target.
 *
 * JSON params:
 * - diceCount: Int (default 1)
 * - diceType: String (D4/D6/D8/D10/D12, default D6)
 * - damageType: String (identifier, default "boundbyfate-core:force")
 * - bonusStat: String? (stat identifier — adds D&D modifier)
 * - bonusFlat: Int (flat bonus, default 0)
 * - bonusLevel: Boolean (add source entity level, default false)
 * - halfOnSave: Boolean (deal half damage if target passed saving throw, default true)
 */
class DamageEffect(
    private val diceCount: Int = 1,
    private val diceType: DiceType = DiceType.D6,
    private val damageTypeId: Identifier = Identifier("boundbyfate-core", "force"),
    private val bonusStatId: Identifier? = null,
    private val bonusFlat: Int = 0,
    private val bonusLevel: Boolean = false,
    private val halfOnSave: Boolean = true
) : BbfEffect {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun apply(context: BbfEffectContext): Boolean {
        var bonus = bonusFlat
        if (bonusLevel) bonus += context.sourceLevel
        if (bonusStatId != null) {
            bonus += context.sourceStats?.getStatValue(bonusStatId)?.dndModifier ?: 0
        }

        val roll = DiceRoller.roll(diceCount, diceType, bonus)
        val baseDamage = roll.total.toFloat()
        context.putExtra("last_damage", baseDamage)

        val damageSource = BbfDamage.of(
            context.world,
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, damageTypeId),
            context.source
        )

        var anyHit = false
        for (target in context.targets) {
            val saved = context.getExtra<Boolean>("save_${target.uuid}") ?: false
            val amount = if (saved && halfOnSave) baseDamage / 2f else baseDamage
            if (target.damage(damageSource, amount)) {
                anyHit = true
                logger.debug("DamageEffect: dealt $amount to ${target.name.string}")
            }
        }
        return anyHit
    }
}
