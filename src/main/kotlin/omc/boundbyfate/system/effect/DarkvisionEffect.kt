package omc.boundbyfate.system.effect

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.api.effect.BbfEffect
import omc.boundbyfate.api.effect.BbfEffectContext
import omc.boundbyfate.component.DarkvisionData
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.registry.BbfAttachments

/**
 * Grants darkvision by modifying lightmap directly.
 * 
 * Uses LightmapMixin to brighten dark areas and applies desaturation
 * based on actual block light levels (not pixel brightness).
 */
class DarkvisionEffect(private val rangeFt: Int = 60) : BbfEffect {

    override fun apply(context: BbfEffectContext): Boolean {
        val player = context.source as? ServerPlayerEntity ?: return false

        // Store darkvision data for client
        player.setAttached(BbfAttachments.DARKVISION, DarkvisionData(rangeFt))

        // Sync to client for lightmap modification and desaturation
        val buf = PacketByteBufs.create()
        buf.writeInt(rangeFt)
        ServerPlayNetworking.send(player, BbfPackets.SYNC_DARKVISION, buf)

        return true
    }
}
