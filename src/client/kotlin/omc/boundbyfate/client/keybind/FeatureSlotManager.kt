package omc.boundbyfate.client.keybind

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.util.Identifier
import omc.boundbyfate.network.BbfPackets

/**
 * Manages feature slot assignments and handles keybind presses.
 *
 * Slots 1-9 can each hold one feature ID.
 * When a slot key is pressed, sends USE_FEATURE packet to server.
 *
 * Slot assignments are stored client-side and can be configured
 * via the feature HUD (future implementation).
 */
object FeatureSlotManager {
    private val slots: Array<Identifier?> = arrayOfNulls(FeatureKeyBindings.SLOT_COUNT)

    /**
     * Assigns a feature to a slot.
     * @param slot 0-indexed slot number (0-8)
     * @param featureId The feature to assign, or null to clear
     */
    fun assignSlot(slot: Int, featureId: Identifier?) {
        if (slot in 0 until FeatureKeyBindings.SLOT_COUNT) {
            slots[slot] = featureId
        }
    }

    fun getSlot(slot: Int): Identifier? = slots.getOrNull(slot)

    /**
     * Called every client tick to check for keybind presses.
     */
    fun tick(client: MinecraftClient) {
        if (client.player == null || client.currentScreen != null) return

        for (i in 0 until FeatureKeyBindings.SLOT_COUNT) {
            val keybind = FeatureKeyBindings.slots[i]
            val featureId = slots[i]

            if (keybind.wasPressed() && featureId != null) {
                sendUseFeature(featureId, client)
            }
        }
    }

    private fun sendUseFeature(featureId: Identifier, client: MinecraftClient) {
        // Get current target (crosshair entity)
        val target = client.targetedEntity

        val buf = PacketByteBufs.create()
        buf.writeIdentifier(featureId)
        buf.writeBoolean(target != null)
        if (target != null) {
            buf.writeUuid(target.uuid)
        }

        ClientPlayNetworking.send(BbfPackets.USE_FEATURE, buf)
    }
}
