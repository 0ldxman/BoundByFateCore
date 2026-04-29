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
        ClientPlayNetworking.registerGlobalReceiver(PlaySoundPacket.TYPE) { packet, player, sender ->
            val world = player.world

            if (packet.positional) {
                world.playSound(
                    player,
                    packet.x, packet.y, packet.z,
                    packet.sound,
                    packet.category,
                    packet.volume,
                    packet.pitch
                )
            } else {
                MinecraftClient.getInstance().soundManager.play(
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


