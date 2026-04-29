package omc.boundbyfate.client.visual

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import omc.boundbyfate.network.packet.s2c.SpawnParticlesPacket

/**
 * Клиентский обработчик пакета [SpawnParticlesPacket].
 *
 * Получает список позиций от сервера и вызывает [world.addParticle]
 * для каждой — рендеринг полностью на стороне Minecraft.
 */
@Environment(EnvType.CLIENT)
object ParticlePacketHandler {

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(SpawnParticlesPacket.TYPE) { packet, player, sender ->
            val world = player.world
            for (pos in packet.positions) {
                world.addParticle(
                    packet.particle,
                    packet.force,
                    pos.x,
                    pos.y,
                    pos.z,
                    packet.velocityX,
                    packet.velocityY,
                    packet.velocityZ
                )
            }
        }
    }
}


