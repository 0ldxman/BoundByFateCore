package omc.boundbyfate.system.proficiency.penalty

import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.util.Identifier
import omc.boundbyfate.api.proficiency.PenaltyContext
import omc.boundbyfate.api.proficiency.PenaltyEffect
import java.util.UUID

/**
 * Reduces attack damage by a multiplier when proficiency is missing.
 *
 * JSON params: { "multiplier": 0.5 }
 * multiplier = 0.5 means 50% of normal damage
 */
class AttackDamagePenalty(private val multiplier: Float) : PenaltyEffect {
    companion object {
        val MODIFIER_UUID: UUID = UUID.fromString("bbf00010-0000-0000-0000-000000000001")
        const val MODIFIER_NAME = "BoundByFate no proficiency damage penalty"
    }

    override fun apply(context: PenaltyContext) {
        val attribute = context.player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE) ?: return

        // Remove old modifier
        attribute.getModifier(MODIFIER_UUID)?.let { attribute.removeModifier(it) }

        // Apply multiplicative penalty: (multiplier - 1.0) as MULTIPLY_TOTAL
        // e.g. multiplier=0.5 → -0.5 → 50% damage
        val modifier = EntityAttributeModifier(
            MODIFIER_UUID,
            MODIFIER_NAME,
            (multiplier - 1.0).toDouble(),
            EntityAttributeModifier.Operation.MULTIPLY_TOTAL
        )
        attribute.addTemporaryModifier(modifier)
    }
}
