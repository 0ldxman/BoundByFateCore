package omc.boundbyfate.client.animation

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import omc.boundbyfate.network.packet.s2c.PlayPlayerAnimPacket
import org.slf4j.LoggerFactory

/**
 * Клиентский обработчик пакета [PlayPlayerAnimPacket].
 *
 * Получает команду от сервера и передаёт в [PlayerAnimSystem].
 *
 * Регистрируется из [omc.boundbyfate.client.BoundByFateCoreClient].
 */
@Environment(EnvType.CLIENT)
object PlayerAnimPacketHandler {

    private val logger = LoggerFactory.getLogger(PlayerAnimPacketHandler::class.java)

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(PlayPlayerAnimPacket.TYPE) { packet, _, _ ->
            // Выполняем на главном потоке — playerAnimator не потокобезопасен
            MinecraftClient.getInstance().execute {
                handlePacket(packet)
            }
        }
        logger.info("PlayerAnimPacketHandler registered")
    }

    private fun handlePacket(packet: PlayPlayerAnimPacket) {
        val animId = packet.animId

        if (animId == null) {
            PlayerAnimSystem.stop(packet.entityId, packet.layer)
            logger.debug("Stopped anim layer '{}' for entity {}", packet.layer ?: "base", packet.entityId)
        } else {
            PlayerAnimSystem.play(packet.entityId, animId, packet.looping, packet.layer)
            logger.debug(
                "Playing '{}' (looping={}, layer='{}') for entity {}",
                animId, packet.looping, packet.layer ?: "base", packet.entityId
            )
        }
    }
}
