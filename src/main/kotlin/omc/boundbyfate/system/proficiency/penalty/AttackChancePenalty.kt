package omc.boundbyfate.system.proficiency.penalty

import omc.boundbyfate.api.proficiency.PenaltyContext
import omc.boundbyfate.api.proficiency.PenaltyEffect

/**
 * Adds a miss chance when attacking without proficiency.
 *
 * JSON params: { "missChance": 0.4 }
 * missChance = 0.4 means 40% chance to miss
 *
 * The actual miss logic is handled in the attack mixin which reads
 * this value from the player's active penalties.
 */
class AttackChancePenalty(val missChance: Float) : PenaltyEffect {
    override fun apply(context: PenaltyContext) {
        // Miss chance is stored and read by the attack mixin
        // This apply() is called to register the active penalty
        ActivePenaltyTracker.setMissChance(context.player, missChance)
    }
}
