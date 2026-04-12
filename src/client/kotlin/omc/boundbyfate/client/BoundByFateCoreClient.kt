package omc.boundbyfate.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import omc.boundbyfate.client.gui.CharacterScreen
import omc.boundbyfate.client.gui.FeatureScreen
import omc.boundbyfate.client.keybind.FeatureKeyBindings
import omc.boundbyfate.client.keybind.FeatureSlotManager
import omc.boundbyfate.client.network.ClientPacketHandler
import omc.boundbyfate.client.render.FloatingTextRenderer
import omc.boundbyfate.client.tooltip.ItemTooltipManager
import omc.boundbyfate.client.tooltip.ProficiencyTooltipProvider
import omc.boundbyfate.client.tooltip.WeaponTooltipProvider

object BoundByFateCoreClient : ClientModInitializer {
    override fun onInitializeClient() {
        // Register packet handlers
        ClientPacketHandler.register()

        // Register tooltip providers
        ItemTooltipManager.register(ProficiencyTooltipProvider)
        ItemTooltipManager.register(WeaponTooltipProvider)

        // Register floating text renderer
        WorldRenderEvents.LAST.register { context -> FloatingTextRenderer.render(context) }

        // Tick floating texts
        ClientTickEvents.END_CLIENT_TICK.register { _ -> FloatingTextRenderer.tick() }

        // Register keybindings
        FeatureKeyBindings.register()

        // Add buttons to inventory screen
        ScreenEvents.AFTER_INIT.register { client, screen, scaledWidth, scaledHeight ->
            if (screen is InventoryScreen) {
                val x = (scaledWidth - 176) / 2
                val y = (scaledHeight - 166) / 2

                Screens.getButtons(screen).add(
                    ButtonWidget.builder(Text.literal("📜")) {
                        client.setScreen(CharacterScreen())
                    }.dimensions(x - 24, y, 20, 20).build()
                )

                Screens.getButtons(screen).add(
                    ButtonWidget.builder(Text.literal("⚔")) {
                        client.setScreen(FeatureScreen())
                    }.dimensions(x - 24, y + 22, 20, 20).build()
                )
            }
        }

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
