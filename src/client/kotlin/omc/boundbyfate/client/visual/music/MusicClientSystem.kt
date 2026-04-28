package omc.boundbyfate.client.visual.music

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.sound.SoundCategory
import omc.boundbyfate.client.transfer.FileCache
import omc.boundbyfate.client.transfer.FileTransferClientSystem
import omc.boundbyfate.network.packet.s2c.MusicStatePacket
import omc.boundbyfate.system.transfer.FileCategory
import omc.boundbyfate.system.visual.sound.MusicState
import org.lwjgl.openal.AL10.*
import org.slf4j.LoggerFactory

/**
 * Клиентская музыкальная система.
 *
 * Воспроизводит три зациклённых трека через OpenAL и управляет их громкостью
 * на основе [MusicState] полученного от сервера.
 *
 * ## Громкость
 * Итоговая громкость трека = координата ползунка × настройка музыки в Minecraft.
 * Это позволяет игроку регулировать громкость через стандартные настройки.
 *
 * ## Ползунок
 * Сервер присылает обновления позиции с throttle 100мс.
 * Клиент применяет позицию напрямую — плавность обеспечивается
 * ограниченной скоростью ползунка на стороне ГМ.
 */
@Environment(EnvType.CLIENT)
object MusicClientSystem {

    private val logger = LoggerFactory.getLogger(MusicClientSystem::class.java)

    /** Текущее состояние музыки от сервера. */
    private var state = MusicState()

    /** Три OpenAL плеера для трёх слотов. */
    private val players = arrayOf(
        MusicPlayer("A"),
        MusicPlayer("B"),
        MusicPlayer("C")
    )

    // ── Инициализация ─────────────────────────────────────────────────────

    fun register() {
        // Получаем состояние музыки от сервера
        ClientPlayNetworking.registerGlobalReceiver(MusicStatePacket.ID) { packet, context ->
            context.client().execute {
                onStateReceived(packet.state, context.client())
            }
        }

        // Подписываемся на получение музыкальных файлов
        FileTransferClientSystem.onFileReceived(FileCategory.MUSIC) { fileId, bytes, extension ->
            onTrackReceived(fileId, bytes, extension)
        }

        // Каждый тик обновляем громкости
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            tick(client)
        }

        logger.info("MusicClientSystem registered")
    }

    // ── Обработка событий ─────────────────────────────────────────────────

    /**
     * Получено новое состояние от сервера.
     * Обновляем треки в слотах и позицию ползунка.
     */
    private fun onStateReceived(newState: MusicState, client: MinecraftClient) {
        val oldState = state
        state = newState

        // Обновляем треки в слотах если изменились
        updateSlotIfChanged(0, oldState.trackA, newState.trackA)
        updateSlotIfChanged(1, oldState.trackB, newState.trackB)
        updateSlotIfChanged(2, oldState.trackC, newState.trackC)

        // Громкости обновятся в следующем tick()
    }

    /**
     * Получен новый музыкальный файл через FileTransferSystem.
     * Если он назначен на слот — загружаем в плеер.
     */
    fun onTrackReceived(fileId: String, bytes: ByteArray, extension: String) {
        val slotIndex = when (fileId) {
            state.trackA -> 0
            state.trackB -> 1
            state.trackC -> 2
            else -> return // файл получен но ещё не назначен на слот
        }
        players[slotIndex].load(fileId, bytes, extension)
        players[slotIndex].play()
        logger.info("Track loaded into slot ${listOf("A","B","C")[slotIndex]}: $fileId")
    }

    // ── Тик ──────────────────────────────────────────────────────────────

    /**
     * Каждый тик обновляем громкости треков.
     */
    private fun tick(client: MinecraftClient) {
        if (!state.hasAnyTrack) return

        // Громкость музыки из настроек Minecraft (0.0 - 1.0)
        val masterVolume = client.options.getSoundVolume(SoundCategory.MUSIC)

        players[0].setVolume(state.volumeA * masterVolume)
        players[1].setVolume(state.volumeB * masterVolume)
        players[2].setVolume(state.volumeC * masterVolume)
    }

    // ── Управление слотами ────────────────────────────────────────────────

    private fun updateSlotIfChanged(slotIndex: Int, oldTrackId: String?, newTrackId: String?) {
        if (oldTrackId == newTrackId) return

        if (newTrackId == null) {
            // Слот очищен — останавливаем плеер
            players[slotIndex].stop()
            return
        }

        // Новый трек — пробуем загрузить из кеша
        val extension = findExtensionInCache(newTrackId)
        if (extension != null) {
            val bytes = FileCache.load(FileCategory.MUSIC, newTrackId, extension)
            if (bytes != null) {
                players[slotIndex].load(newTrackId, bytes, extension)
                players[slotIndex].play()
                logger.info("Track loaded from cache into slot ${listOf("A","B","C")[slotIndex]}: $newTrackId")
            } else {
                logger.warn("Track $newTrackId not in cache yet, waiting for download")
            }
        }
    }

    /** Ищет расширение файла в кеше (ogg, mp3, wav). */
    private fun findExtensionInCache(fileId: String): String? {
        return listOf("ogg", "mp3", "wav").firstOrNull { ext ->
            FileCache.exists(FileCategory.MUSIC, fileId, ext)
        }
    }

    // ── Shutdown ──────────────────────────────────────────────────────────

    fun shutdown() {
        players.forEach { it.stop() }
    }
}


