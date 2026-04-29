package omc.boundbyfate.client.visual

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import omc.boundbyfate.network.packet.s2c.PlaySoundPacket

/**
 * Клиентский обработчик пакета [PlaySoundPacket].
 *
 * Environment звуки воспроизводятся позиционно через Minecraft sound engine.
 * GUI звуки воспроизводятся без позиционирования прямо у игрока.
 */
@Environment(EnvType.CLIENT)
object SoundPacketHandler {

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(PlaySoundPacket.TYPE) { client, player, packet, sender ->
            client.execute {
                val world = client.world ?: return@execute

                if (packet.positional) {
                    // Environment звук — позиционный
                    world.playSound(
                        client.player,
                        packet.x, packet.y, packet.z,
                        packet.sound,
                        packet.category,
                        packet.volume,
                        packet.pitch
                    )
                } else {
                    // GUI звук — без позиционирования, прямо у игрока
                    client.soundManager.play(
                        net.minecraft.client.sound.PositionedSoundInstance.master(
                            packet.sound,
                            packet.pitch,
                            packet.volume
                        )
                    )
                }
            }
        }
    }
}


