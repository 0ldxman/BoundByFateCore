package omc.boundbyfate.system.visual.sound

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.network.extension.sendPacket
import omc.boundbyfate.network.packet.c2s.MusicSliderUpdatePacket
import omc.boundbyfate.network.packet.c2s.MusicTrackAssignPacket
import omc.boundbyfate.network.packet.s2c.MusicStatePacket
import omc.boundbyfate.system.transfer.FileCategory
import omc.boundbyfate.system.transfer.FileTransferSystem
import org.slf4j.LoggerFactory

/**
 * Серверная система музыки.
 *
 * Хранит глобальное состояние [MusicState] и синхронизирует его с клиентами.
 *
 * ## Поток данных
 * ```
 * ГМ двигает ползунок (throttle 100мс)
 *   → MusicSliderUpdatePacket (C2S)
 *   → MusicSystem.onSliderUpdate()
 *   → обновляет state
 *   → MusicStatePacket (S2C) → все клиенты
 *   → MusicClientSystem обновляет громкости треков
 * ```
 */
object MusicSystem {

    private val logger = LoggerFactory.getLogger(MusicSystem::class.java)

    /** Текущее глобальное состояние музыки. */
    private var state = MusicState()

    private lateinit var server: MinecraftServer

    // ── Инициализация ─────────────────────────────────────────────────────

    fun register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register { srv ->
            server = srv
        }

        // При подключении игрока — синхронизируем текущее состояние
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            handler.player.sendPacket(MusicStatePacket(state))
        }

        // Подписываемся на получение музыкальных файлов через FileTransferSystem
        FileTransferSystem.onFileReceived(FileCategory.MUSIC) { fileId, _, extension ->
            logger.info("Music track received: $fileId.$extension")
            // Трек доступен — клиенты получат его через FileTransferSystem
            // Назначение на слот делается отдельно через MusicTrackAssignPacket
        }

        logger.info("MusicSystem registered")
    }

    // ── Обработка пакетов от ГМ ───────────────────────────────────────────

    /**
     * ГМ обновил позицию ползунка.
     * Вызывается на server thread.
     */
    fun onSliderUpdate(packet: MusicSliderUpdatePacket, sender: ServerPlayerEntity) {
        if (!sender.hasPermissionLevel(2)) return

        state = state.withPosition(packet.u, packet.v)
        broadcastState()
    }

    /**
     * ГМ назначил трек на слот.
     * Вызывается на server thread.
     */
    fun onTrackAssign(packet: MusicTrackAssignPacket, sender: ServerPlayerEntity) {
        if (!sender.hasPermissionLevel(2)) return

        state = when (packet.slot) {
            0 -> state.withTrackA(packet.trackId)
            1 -> state.withTrackB(packet.trackId)
            2 -> state.withTrackC(packet.trackId)
            else -> {
                logger.warn("Invalid music slot: ${packet.slot}")
                return
            }
        }

        val slotName = listOf("A", "B", "C")[packet.slot]
        logger.info("Music slot $slotName assigned: ${packet.trackId ?: "cleared"}")

        broadcastState()
    }

    // ── Утилиты ───────────────────────────────────────────────────────────

    /**
     * Рассылает текущее состояние всем игрокам.
     */
    private fun broadcastState() {
        val packet = MusicStatePacket(state)
        server.playerManager.playerList.forEach { it.sendPacket(packet) }
    }

    /**
     * Возвращает текущее состояние (для отладки/команд).
     */
    fun getState(): MusicState = state
}
