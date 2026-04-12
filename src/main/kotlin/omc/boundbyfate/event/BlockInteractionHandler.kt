package omc.boundbyfate.event

import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import omc.boundbyfate.system.proficiency.ProficiencySystem

/**
 * Handles block interaction checks for proficiency system.
 * Uses Fabric's UseBlockCallback instead of a mixin.
 */
object BlockInteractionHandler {

    fun register() {
        UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
            if (world.isClient || player !is ServerPlayerEntity) return@register ActionResult.PASS

            val block = world.getBlockState(hitResult.blockPos).block
            val blocked = ProficiencySystem.getBlockedEntry(player, block)

            if (blocked != null) {
                player.sendMessage(
                    Text.literal("§cТребуется владение: ${blocked.displayName}"),
                    true // action bar
                )
                return@register ActionResult.FAIL
            }

            ActionResult.PASS
        }
    }
}
