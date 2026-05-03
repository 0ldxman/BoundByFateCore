package omc.boundbyfate.client.network

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import omc.boundbyfate.client.gui.screens.CharacterEditScreen
import omc.boundbyfate.network.packet.s2c.OpenCharacterEditScreenPacket

/**
 * Обработчик клиентских пакетов.
 */
object ClientPacketHandler {

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(OpenCharacterEditScreenPacket.ID) { client, _, _, _ ->
            client.execute {
                MinecraftClient.getInstance().setScreen(CharacterEditScreen())
            }
        }
    }
}
