package omc.boundbyfate.data.world.sync

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.nbt.NbtIo
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.data.world.BbfWorldData
import omc.boundbyfate.data.world.core.SyncStrategy
import omc.boundbyfate.data.world.core.WorldDataSection
import omc.boundbyfate.network.core.PacketSender
import omc.boundbyfate.network.packet.s2c.SyncSectionPacket
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Обработчик синхронизации секций WorldData с клиентами.
 *
 * Реализует адресную синхронизацию:
 * - При входе игрока — синхронизировать секции с [SyncStrategy.OnJoin] и [SyncStrategy.ToAll]
 * - Каждый тик — синхронизировать dirty секции согласно их стратегии
 * - При остановке сервера — инвалидировать кэш
 *
 * ## Регистрация
 *
 * ```kotlin
 * WorldDataSyncHandler.register()
 * ```
 */
object WorldDataSyncHandler {

    private val logger = LoggerFactory.getLogger(WorldDataSyncHandler::class.java)

    fun register() {
        // При входе игрока — синхронизировать нужные секции
        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            val player = handler.player
            syncOnJoin(player, server)
        }

        // Каждый тик — синхронизировать dirty секции
        ServerTickEvents.END_SERVER_TICK.register { server ->
            syncDirtySections(server)
        }

        // При остановке сервера — инвалидировать кэш
        ServerLifecycleEvents.SERVER_STOPPED.register {
            // Кэш инвалидируется автоматически через BbfWorldData.registerLifecycle()
        }

        logger.info("WorldDataSyncHandler registered")
    }

    // ── Синхронизация при входе ───────────────────────────────────────────

    private fun syncOnJoin(player: ServerPlayerEntity, server: MinecraftServer) {
        val worldData = BbfWorldData.get(server)

        for (entry in BbfWorldData.getSyncableEntries()) {
            val shouldSync = when (entry.syncStrategy) {
                is SyncStrategy.ToAll  -> true
                is SyncStrategy.OnJoin -> true
                else                   -> false
            }
            if (!shouldSync) continue

            val section = worldData.getSection(entry)
            sendSectionPacket(player, entry.id.toString(), section)
        }
    }

    // ── Синхронизация dirty секций ────────────────────────────────────────

    private fun syncDirtySections(server: MinecraftServer) {
        val worldData = BbfWorldData.get(server)

        for (entry in BbfWorldData.getSyncableEntries()) {
            val section = worldData.getSection(entry)
            if (!section.isDirty) continue

            val recipients = when (val strategy = entry.syncStrategy) {
                is SyncStrategy.None   -> continue
                is SyncStrategy.ToAll  -> server.playerManager.playerList
                is SyncStrategy.OnJoin -> continue  // только при входе
                is SyncStrategy.ToOwner -> strategy.ownerExtractor(server)
                is SyncStrategy.Custom  -> strategy.recipients(server)
            }

            if (recipients.isEmpty()) continue

            val sectionIdStr = entry.id.toString()
            recipients.forEach { player ->
                sendSectionPacket(player, sectionIdStr, section)
            }
        }
    }

    // ── Отправка пакета ───────────────────────────────────────────────────

    private fun sendSectionPacket(
        player: ServerPlayerEntity,
        sectionId: String,
        section: WorldDataSection
    ) {
        try {
            val nbt = section.toNbt()
            val baos = ByteArrayOutputStream()
            NbtIo.write(nbt, DataOutputStream(baos))
            val bytes = baos.toByteArray()

            val packet = SyncSectionPacket(
                sectionId = net.minecraft.util.Identifier(
                    sectionId.substringBefore(':'),
                    sectionId.substringAfter(':')
                ),
                data = bytes
            )
            PacketSender.send(player, packet)
        } catch (e: Exception) {
            logger.error("Failed to sync section '$sectionId' to player ${player.name.string}", e)
        }
    }
}

