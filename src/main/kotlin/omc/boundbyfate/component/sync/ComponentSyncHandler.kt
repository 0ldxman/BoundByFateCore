package omc.boundbyfate.component.sync

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.entity.LivingEntity
import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.component.core.BbfComponent
import omc.boundbyfate.component.core.BbfComponents
import omc.boundbyfate.component.core.ComponentEntry
import omc.boundbyfate.component.core.SyncMode
import omc.boundbyfate.component.core.getComponent
import omc.boundbyfate.component.core.toBytes
import omc.boundbyfate.network.core.PacketSender
import omc.boundbyfate.network.packet.s2c.SyncComponentPacket
import org.slf4j.LoggerFactory

/**
 * Обработчик синхронизации компонентов сервер → клиент.
 *
 * ## Стратегия синхронизации
 *
 * Вместо O(n × компоненты × игроки) итерации каждый тик используем
 * [DirtyEntityTracker]: сущности попадают в него только когда их компонент
 * реально изменился. Тик обрабатывает только dirty сущности.
 *
 * ### Три пути синхронизации:
 *
 * 1. **ON_JOIN** — при входе игрока синхронизируем все его компоненты
 * 2. **ON_CHANGE** — через [DirtyEntityTracker], только изменённые сущности
 * 3. **EVERY_TICK** — отдельный список, синхронизируется каждый тик для всех игроков
 *
 * ### Сложность:
 * - Было: O(сущности × компоненты × игроки) каждый тик
 * - Стало: O(dirty_сущности × компоненты × игроки) + O(игроки × EVERY_TICK_компоненты)
 *   где dirty_сущности << все_сущности в типичном случае
 */
object ComponentSyncHandler {

    private val logger = LoggerFactory.getLogger(ComponentSyncHandler::class.java)

    /**
     * Кэш компонентов с SyncMode.EVERY_TICK — вычисляется один раз.
     * Обновляется при изменении регистрации компонентов (не происходит в рантайме).
     */
    private val everyTickEntries by lazy {
        BbfComponents.getEntriesBySyncMode(SyncMode.EVERY_TICK)
    }

    fun register() {
        // При входе игрока — синхронизировать все компоненты кроме NONE
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player
            logger.debug("Syncing components on join for ${player.name.string}")
            syncAllComponents(player, player)
        }

        // При загрузке сущности — синхронизировать ON_CHANGE компоненты для всех игроков
        ServerEntityEvents.ENTITY_LOAD.register { entity, world ->
            if (entity !is LivingEntity) return@register
            world.players.filterIsInstance<ServerPlayerEntity>().forEach { player ->
                syncEntityComponents(entity, player, SyncMode.ON_CHANGE)
            }
        }

        // При выгрузке сущности — убираем из трекера
        ServerEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
            if (entity is LivingEntity) {
                DirtyEntityTracker.remove(entity)
            }
        }

        // Тик — только dirty сущности + EVERY_TICK компоненты игроков
        ServerTickEvents.END_SERVER_TICK.register { server ->
            // 1. EVERY_TICK компоненты — для всех игроков каждый тик
            if (everyTickEntries.isNotEmpty()) {
                for (player in server.playerManager.playerList) {
                    syncEveryTickComponents(player)
                }
            }

            // 2. ON_CHANGE компоненты — только dirty сущности
            val dirty = DirtyEntityTracker.drainDirty()
            if (dirty.isEmpty()) return@register

            // Собираем игроков один раз, не в каждой итерации
            val allPlayers = server.playerManager.playerList

            for (entity in dirty) {
                // Сущность могла быть удалена из мира пока висела в dirty-set
                if (entity.isRemoved) continue

                if (entity is ServerPlayerEntity) {
                    // Игрок — синхронизируем только с ним самим
                    syncOnChangeComponents(entity, listOf(entity))
                } else {
                    // НПС/моб — синхронизируем со всеми игроками в мире
                    val world = entity.world
                    val watchers = if (world is net.minecraft.server.world.ServerWorld) {
                        world.players.filterIsInstance<ServerPlayerEntity>()
                    } else {
                        allPlayers
                    }
                    syncOnChangeComponents(entity, watchers)
                }
            }
        }

        logger.info("ComponentSyncHandler registered (DirtyEntityTracker mode)")
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

    // ── Синхронизация EVERY_TICK ──────────────────────────────────────────

    private fun syncEveryTickComponents(player: ServerPlayerEntity) {
        val registries = player.server.registryManager
        for (entry in everyTickEntries) {
            val component = player.getComponent(entry.attachmentType) ?: continue
            sendComponentPacket(player, entry, component, player, registries)
        }
    }

    // ── Синхронизация dirty компонентов ──────────────────────────────────

    /**
     * Синхронизирует dirty ON_CHANGE компоненты сущности со списком игроков.
     * После отправки всем — сбрасывает dirty флаг.
     *
     * Порядок важен: сначала отправляем всем watchers, потом markClean.
     * Иначе второй watcher не получит данные.
     */
    private fun syncOnChangeComponents(
        entity: LivingEntity,
        watchers: List<ServerPlayerEntity>
    ) {
        if (watchers.isEmpty()) return
        val registries = watchers.first().server.registryManager
        val onChangeEntries = BbfComponents.getEntriesBySyncMode(SyncMode.ON_CHANGE)

        for (entry in onChangeEntries) {
            val component = entity.getComponent(entry.attachmentType) ?: continue
            if (!component.isDirty) continue

            // Отправляем всем watchers
            for (watcher in watchers) {
                sendComponentPacket(entity, entry, component, watcher, registries)
            }

            // Сбрасываем dirty только после отправки всем
            component.markClean()
        }
    }

    // ── Синхронизация компонентов сущности ────────────────────────────────

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
