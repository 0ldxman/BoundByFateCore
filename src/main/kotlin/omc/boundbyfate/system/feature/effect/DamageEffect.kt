package omc.boundbyfate.system.feature.effect

import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.damage.DamageType
import net.minecraft.registry.RegistryKey
import net.minecraft.util.Identifier
import omc.boundbyfate.api.damage.BbfDamage
import omc.boundbyfate.api.dice.DiceRoller
import omc.boundbyfate.api.dice.DiceType
import omc.boundbyfate.api.feature.FeatureContext
import omc.boundbyfate.api.feature.FeatureEffect

/**
 * Deals damage to each target.
 *
 * JSON params:
 * - diceCount: Int (default 1)
 * - diceType: String (D4/D6/D8/D10/D12, default D6)
 * - damageType: String (identifier, default "boundbyfate-core:force")
 * - bonusStat: String? (stat identifier - adds dnd modifier)
 * - bonusFlat: Int (flat bonus, default 0)
 * - bonusLevel: Boolean (add caster level, default false)
 */
class DamageEffect(
    private val diceCount: Int = 1,
    private val diceType: DiceType = DiceType.D6,
    private val damageTypeId: Identifier = Identifier("boundbyfate-core", "force"),
    private val bonusStatId: Identifier? = null,
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
        val source = BbfDamage.of(context.world, RegistryKey.of(RegistryKeys.DAMAGE_TYPE, damageTypeId), context.caster)

        for (target in context.targets) {
            target.damage(source, roll.total.toFloat())
        }
    }
}
