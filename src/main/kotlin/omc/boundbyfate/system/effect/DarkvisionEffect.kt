package omc.boundbyfate.system.effect

import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.api.effect.BbfEffect
import omc.boundbyfate.api.effect.BbfEffectContext
import omc.boundbyfate.component.DarkvisionData
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.registry.BbfAttachments
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import org.slf4j.LoggerFactory

/**
 * Grants darkvision to the source entity.
 * Stores the range in an attachment and syncs to client.
 *
 * JSON params:
 * - rangeFt: Int (default 60)
 */
class DarkvisionEffect(
    private val rangeFt: Int = 60
) : BbfEffect {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun apply(context: BbfEffectContext): Boolean {
        val player = context.source as? ServerPlayerEntity ?: return false

        // Store darkvision data
        val existing = player.getAttachedOrElse(BbfAttachments.DARKVISION, null)
        // Keep the larger range if multiple sources grant darkvision
        val newRange = if (existing != null) maxOf(existing.rangeFt, rangeFt) else rangeFt
        player.setAttached(BbfAttachments.DARKVISION, DarkvisionData(newRange))

        // Sync to client
        val buf = PacketByteBufs.create()
        buf.writeInt(newRange)
        ServerPlayNetworking.send(player, BbfPackets.SYNC_DARKVISION, buf)

        logger.debug("Granted darkvision ${newRange}ft to ${player.name.string}")
        return true
    }
}
