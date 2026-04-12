package omc.boundbyfate.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import omc.boundbyfate.client.keybind.FeatureKeyBindings
import omc.boundbyfate.client.keybind.FeatureSlotManager
import omc.boundbyfate.client.network.ClientPacketHandler

object BoundByFateCoreClient : ClientModInitializer {
    override fun onInitializeClient() {
        // Register packet handlers
        ClientPacketHandler.register()

        // Register keybindings (slots 1-9)
        FeatureKeyBindings.register()

        // Check keybind presses every tick
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            FeatureSlotManager.tick(client)
        }
    }
}
