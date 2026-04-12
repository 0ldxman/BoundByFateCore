package omc.boundbyfate.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import omc.boundbyfate.client.gui.CharacterScreen
import omc.boundbyfate.client.gui.FeatureScreen
import omc.boundbyfate.client.keybind.FeatureKeyBindings
import omc.boundbyfate.client.keybind.FeatureSlotManager
import omc.boundbyfate.client.network.ClientPacketHandler

object BoundByFateCoreClient : ClientModInitializer {
    override fun onInitializeClient() {
        // Register packet handlers
        ClientPacketHandler.register()

        // Register keybindings
        FeatureKeyBindings.register()

        // Client tick
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            // Feature slot activation
            FeatureSlotManager.tick(client)

            // Open feature screen
            if (FeatureKeyBindings.openFeatureScreen.wasPressed()) {
                client.setScreen(FeatureScreen())
            }

            // Open character sheet
            if (FeatureKeyBindings.openCharacterSheet.wasPressed()) {
                client.setScreen(CharacterScreen())
            }
        }
    }
}
