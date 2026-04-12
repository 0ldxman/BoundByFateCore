package omc.boundbyfate.system.proficiency.penalty

import net.minecraft.text.Text
import omc.boundbyfate.api.proficiency.PenaltyContext
import omc.boundbyfate.api.proficiency.PenaltyEffect

/**
 * Blocks interaction with a block entirely when proficiency is missing.
 * Shows a message above the hotbar indicating which proficiency is required.
 *
 * JSON params: {} (no params needed)
 */
object BlockInteractionPenalty : PenaltyEffect {
    override fun apply(context: PenaltyContext) {
        // Show action bar message (above hotbar, red text)
        context.player.sendMessage(
            Text.literal("§cТребуется владение: ${context.entryDisplayName}"),
            true // true = action bar
        )
        // Actual blocking is handled in the block interaction mixin
    }
}
