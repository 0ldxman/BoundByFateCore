package omc.boundbyfate.network

import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.system.feature.FeatureSystem
import org.slf4j.LoggerFactory

/**
 * Handles packets received from clients on the server side.
 */
object ServerPacketHandler {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    fun register() {
        // Client → Server: player activates a feature via keybind
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.USE_FEATURE) { server, player, _, buf, _ ->
            val featureId = buf.readIdentifier()
            val hasTarget = buf.readBoolean()
            val targetUuid = if (hasTarget) buf.readUuid() else null

            server.execute {
                handleUseFeature(player, featureId, targetUuid, server)
            }
        }
    }

    private fun handleUseFeature(
        player: ServerPlayerEntity,
        featureId: Identifier,
        targetUuid: java.util.UUID?,
        server: MinecraftServer
    ) {
        // Resolve optional target
        val target = targetUuid?.let { uuid ->
            player.serverWorld.getEntitiesByClass(
                net.minecraft.entity.LivingEntity::class.java,
                player.boundingBox.expand(20.0)
            ) { it.uuid == uuid }.firstOrNull()
        }

        val success = FeatureSystem.execute(player, featureId, target)
        if (!success) {
            logger.debug("Feature $featureId execution failed for ${player.name.string}")
        }
    }
}
