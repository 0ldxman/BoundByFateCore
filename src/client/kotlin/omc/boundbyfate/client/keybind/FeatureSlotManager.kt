package omc.boundbyfate.client.keybind

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import omc.boundbyfate.client.state.ClientFeatureState
import omc.boundbyfate.network.BbfPackets

/**
 * Handles feature slot keybind presses.
 * Reads slot assignments from ClientFeatureState and sends USE_FEATURE packets.
 */
object FeatureSlotManager {

    fun tick(client: MinecraftClient) {
        if (client.player == null || client.currentScreen != null) return

        for (i in 0 until FeatureKeyBindings.SLOT_COUNT) {
            if (FeatureKeyBindings.slots[i].wasPressed()) {
                val featureId = ClientFeatureState.getHotbarSlot(i) ?: continue
                sendUseFeature(featureId, client)
            }
        }
    }

    private fun sendUseFeature(featureId: net.minecraft.util.Identifier, client: MinecraftClient) {
        val target = client.targetedEntity
        val buf = PacketByteBufs.create()
        buf.writeIdentifier(featureId)
        buf.writeBoolean(target != null)
        if (target != null) buf.writeUuid(target.uuid)
        ClientPlayNetworking.send(BbfPackets.USE_FEATURE, buf)
    }
}
