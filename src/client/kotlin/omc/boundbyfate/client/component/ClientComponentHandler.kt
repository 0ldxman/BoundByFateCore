package omc.boundbyfate.client.component

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.LivingEntity
import omc.boundbyfate.component.core.BbfComponents
import omc.boundbyfate.component.core.getComponent
import omc.boundbyfate.component.core.fromBytes
import omc.boundbyfate.network.packet.s2c.SyncComponentPacket
import org.slf4j.LoggerFactory

object ClientComponentHandler {

    private val logger = LoggerFactory.getLogger(ClientComponentHandler::class.java)

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(SyncComponentPacket.TYPE) { packet, player, _ ->
            val client = MinecraftClient.getInstance()
            client.execute {
                handleSyncPacket(packet, client)
            }
        }
        logger.info("ClientComponentHandler registered")
    }

    private fun handleSyncPacket(
        payload: SyncComponentPacket,
        client: MinecraftClient
    ) {
        val world = client.world ?: return

        val entity = world.getEntityById(payload.entityId) as? LivingEntity ?: run {
            logger.warn("Entity ${payload.entityId} not found for component sync '${payload.componentId}'")
            return
        }

        val entry = BbfComponents.getEntry(payload.componentId) ?: run {
            logger.warn("Component '${payload.componentId}' not registered")
            return
        }

        val component = entity.getComponent(entry.attachmentType) ?: run {
            logger.warn("Component '${payload.componentId}' not found on entity ${entity.name.string}")
            return
        }

        try {
            // Use the client's integrated server registries or the network handler's registry manager
            val registries = client.networkHandler?.registryManager
                ?: return

            component.fromBytes(payload.data, registries)
            component.markClean()

            logger.debug("Applied component '${payload.componentId}' to entity ${entity.name.string}")
        } catch (e: Exception) {
            logger.error("Failed to apply component '${payload.componentId}' to entity ${entity.name.string}", e)
        }
    }
}
