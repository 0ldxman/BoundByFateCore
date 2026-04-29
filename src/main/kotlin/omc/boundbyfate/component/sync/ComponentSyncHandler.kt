package omc.boundbyfate.component.sync

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.entity.LivingEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import omc.boundbyfate.component.core.BbfComponent
import omc.boundbyfate.component.core.BbfComponents
import omc.boundbyfate.component.core.ComponentEntry
import omc.boundbyfate.component.core.SyncMode
import omc.boundbyfate.component.core.getComponent
import omc.boundbyfate.component.core.toBytes
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.PacketSender
import omc.boundbyfate.network.packet.s2c.SyncComponentPacket
import org.slf4j.LoggerFactory

/**
 * Обработчик синхронизации компонентов сервер → клиент.
 *
 * Реализует событийную синхронизацию по образцу Hollow Core:
 * - При входе игрока — синхронизировать ON_JOIN и ON_CHANGE компоненты
 * - При начале отслеживания сущности — синхронизировать ON_CHANGE компоненты
 * - Каждый тик — синхронизировать dirty ON_CHANGE и EVERY_TICK компоненты
 *
 * ## Регистрация
 *
 * Вызывается из [omc.boundbyfate.BoundByFateCore.initializeSystems]:
 * ```kotlin
 * ComponentSyncHandler.register()
 * ```
 */
object ComponentSyncHandler {

    private val logger = LoggerFactory.getLogger(ComponentSyncHandler::class.java)

    fun register() {
        // При входе игрока — синхронизировать все компоненты кроме NONE
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player
            logger.debug("Syncing components on join for ${player.name.string}")
            syncAllComponents(player, player)
        }

        // Каждый тик — синхронизировать dirty компоненты
        ServerTickEvents.END_SERVER_TICK.register { server ->
            for (player in server.playerManager.playerList) {
                syncDirtyComponents(player)
            }
            // Синхронизировать dirty компоненты всех живых сущностей в мирах
            for (world in server.worlds) {
                for (entity in world.iterateEntities()) {
                    if (entity !is LivingEntity || entity is ServerPlayerEntity) continue
                    val dirtyEntries = BbfComponents.getSyncableEntries().filter { entry ->
                        entity.getComponent(entry.attachmentType)?.isDirty == true
                    }
                    if (dirtyEntries.isEmpty()) continue
                    val registries = server.registryManager
                    val watchers = world.players.filterIsInstance<ServerPlayerEntity>()
                    for (entry in dirtyEntries) {
                        val component = entity.getComponent(entry.attachmentType) ?: continue
                        for (watcher in watchers) {
                            sendComponentPacket(entity, entry, component, watcher, registries)
                        }
                        component.markClean()
                    }
                }
            }
        }

        // При начале отслеживания сущности другим игроком
        // — синхронизировать ON_CHANGE компоненты этой сущности
        ServerEntityEvents.ENTITY_LOAD.register { entity, world ->
            if (entity !is LivingEntity) return@register
            // Синхронизируем для всех игроков которые видят сущность
            world.players.filterIsInstance<ServerPlayerEntity>().forEach { player ->
                syncEntityComponents(entity, player, SyncMode.ON_CHANGE)
            }
        }

        logger.info("ComponentSyncHandler registered")
    }

    // ── Синхронизация при входе ───────────────────────────────────────────

    /**
     * Синхронизирует все компоненты сущности с игроком.
     * Используется при входе и при начале отслеживания.
     */
    fun syncAllComponents(entity: LivingEntity, target: ServerPlayerEntity) {
        val registries = target.server.registryManager

        for (entry in BbfComponents.getSyncableEntries()) {
            val component = entity.getComponent(entry.attachmentType) ?: continue
            sendComponentPacket(entity, entry, component, target, registries)
        }
    }

    // ── Синхронизация dirty компонентов ──────────────────────────────────

    /**
     * Синхронизирует только изменённые (dirty) компоненты игрока.
     * Вызывается каждый тик.
     */
    private fun syncDirtyComponents(player: ServerPlayerEntity) {
        val registries = player.server.registryManager

        for (entry in BbfComponents.getSyncableEntries()) {
            val component = player.getComponent(entry.attachmentType) ?: continue

            val shouldSync = when (entry.syncMode) {
                SyncMode.EVERY_TICK -> true
                SyncMode.ON_CHANGE  -> component.isDirty
                SyncMode.ON_JOIN    -> false  // только при входе
                SyncMode.NONE       -> false
            }

            if (!shouldSync) continue

            sendComponentPacket(player, entry, component, player, registries)
            component.markClean()
        }
    }

    // ── Синхронизация компонентов сущности ────────────────────────────────

    /**
     * Синхронизирует компоненты сущности с конкретным игроком.
     */
    private fun syncEntityComponents(
        entity: LivingEntity,
        target: ServerPlayerEntity,
        vararg modes: SyncMode
    ) {
        val registries = target.server.registryManager

        for (entry in BbfComponents.getEntriesBySyncMode(*modes)) {
            val component = entity.getComponent(entry.attachmentType) ?: continue
            sendComponentPacket(entity, entry, component, target, registries)
        }
    }

    // ── Отправка пакета ───────────────────────────────────────────────────

    private fun sendComponentPacket(
        entity: LivingEntity,
        entry: ComponentEntry<*>,
        component: BbfComponent,
        target: ServerPlayerEntity,
        registries: net.minecraft.registry.RegistryWrapper.WrapperLookup
    ) {
        try {
            val bytes = component.toBytes(registries)
            val packet = SyncComponentPacket(
                entityId = entity.id,
                componentId = entry.id,
                data = bytes
            )
            PacketSender.send(target, packet)
        } catch (e: Exception) {
            logger.error("Failed to sync component '${entry.id}' for entity ${entity.name.string}", e)
        }
    }
}


