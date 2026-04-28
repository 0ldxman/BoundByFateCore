package omc.boundbyfate.component.sync

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.entity.LivingEntity
import omc.boundbyfate.component.core.BbfComponents
import omc.boundbyfate.component.core.getComponent
import omc.boundbyfate.component.core.fromBytes
import omc.boundbyfate.network.packet.s2c.SyncComponentPacket
import org.slf4j.LoggerFactory

/**
 * Клиентский обработчик синхронизации компонентов.
 *
 * Получает [SyncComponentPacket] от сервера и применяет данные
 * к локальному компоненту сущности.
 *
 * ## Регистрация
 *
 * Вызывается из клиентского инициализатора:
 * ```kotlin
 * ClientComponentHandler.register()
 * ```
 */
object ClientComponentHandler {

    private val logger = LoggerFactory.getLogger(ClientComponentHandler::class.java)

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(SyncComponentPacket.ID) { payload, context ->
            context.client().execute {
                handleSyncPacket(payload, context)
            }
        }

        logger.info("ClientComponentHandler registered")
    }

    private fun handleSyncPacket(
        payload: SyncComponentPacket,
        context: ClientPlayNetworking.Context
    ) {
        val client = context.client()
        val world = client.world ?: return

        // Находим сущность
        val entity = world.getEntityById(payload.entityId) as? LivingEntity ?: run {
            logger.warn("Entity ${payload.entityId} not found for component sync '${payload.componentId}'")
            return
        }

        // Находим запись компонента
        val entry = BbfComponents.getEntry(payload.componentId) ?: run {
            logger.warn("Component '${payload.componentId}' not registered")
            return
        }

        // Получаем или создаём компонент
        val component = entity.getComponent(entry.attachmentType) ?: run {
            logger.warn("Component '${payload.componentId}' not found on entity ${entity.name.string}")
            return
        }

        // Применяем данные
        try {
            val registries = client.networkHandler?.combinedDynamicRegistries
                ?.getCombinedRegistryManager()
                ?: return

            component.fromBytes(payload.data, registries)
            // После десериализации с сервера — компонент чистый
            component.markClean()

            logger.debug(
                "Applied component '${payload.componentId}' to entity ${entity.name.string}"
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to apply component '${payload.componentId}' to entity ${entity.name.string}",
                e
            )
        }
    }
}
