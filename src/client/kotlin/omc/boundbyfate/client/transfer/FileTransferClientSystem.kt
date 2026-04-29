package omc.boundbyfate.client.transfer

import kotlinx.coroutines.*
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import omc.boundbyfate.network.packet.s2c.FileDistributeChunkPacket
import omc.boundbyfate.network.packet.s2c.FileDistributeStartPacket
import omc.boundbyfate.network.packet.s2c.FileSyncListPacket
import omc.boundbyfate.system.transfer.DownloadSession
import omc.boundbyfate.system.transfer.FileCategory
import omc.boundbyfate.system.transfer.FileTransferConfig
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Клиентская система передачи файлов.
 *
 * Получает файлы от сервера, сохраняет в [FileCache] и уведомляет подписчиков.
 * Тяжёлые операции (сборка байтов, запись на диск) выполняются в IO потоке.
 */
@Environment(EnvType.CLIENT)
object FileTransferClientSystem {

    private val logger = LoggerFactory.getLogger(FileTransferClientSystem::class.java)

    private val ioScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineName("FileTransfer-Client-IO")
    )

    /** Активные сессии получения файлов. */
    private val downloadSessions = ConcurrentHashMap<UUID, DownloadSession>()

    /** Обработчики получения файлов: категория → список обработчиков. */
    private val fileHandlers = ConcurrentHashMap<FileCategory, MutableList<ClientFileReceivedHandler>>()

    // ── Регистрация ───────────────────────────────────────────────────────

    fun register() {
        // Список файлов от сервера при подключении
        ClientPlayNetworking.registerGlobalReceiver(FileSyncListPacket.TYPE) { packet, context ->
            context.client().execute {
                onSyncListReceived(packet, context.client())
            }
        }

        // Начало получения файла
        ClientPlayNetworking.registerGlobalReceiver(FileDistributeStartPacket.TYPE) { packet, context ->
            context.client().execute {
                onDistributeStart(packet)
            }
        }

        // Чанк файла
        ClientPlayNetworking.registerGlobalReceiver(FileDistributeChunkPacket.TYPE) { packet, context ->
            context.client().execute {
                onDistributeChunk(packet)
            }
        }

        startSessionCleanup()
        logger.info("FileTransferClientSystem registered")
    }

    // ── Подписка на события ───────────────────────────────────────────────

    /**
     * Регистрирует обработчик получения файла на клиенте.
     *
     * ```kotlin
     * FileTransferClientSystem.onFileReceived(FileCategory.MUSIC) { fileId, bytes, extension ->
     *     MusicClientSystem.onTrackReceived(fileId, bytes, extension)
     * }
     * ```
     */
    fun onFileReceived(category: FileCategory, handler: ClientFileReceivedHandler) {
        fileHandlers.getOrPut(category) { mutableListOf() }.add(handler)
    }

    // ── Обработка пакетов ─────────────────────────────────────────────────

    /**
     * Получен список файлов от сервера.
     * Сравниваем с кешем и запрашиваем отсутствующие.
     */
    private fun onSyncListReceived(packet: FileSyncListPacket, client: MinecraftClient) {
        ioScope.launch {
            val missing = packet.files.filter { meta ->
                !FileCache.exists(meta.category, meta.fileId, meta.extension)
            }

            if (missing.isEmpty()) {
                logger.info("All ${packet.files.size} server files already cached")
                return@launch
            }

            logger.info("Need to download ${missing.size}/${packet.files.size} files")

            // Уведомляем сервер о том какие файлы нужны
            // Сервер сам начнёт раздачу через FileDistributeStartPacket
            // (сервер видит что игрок подключился и отправляет нужные файлы)
            // Здесь просто логируем — реальный запрос идёт через сервер автоматически
        }
    }

    /**
     * Сервер начинает раздачу файла.
     */
    private fun onDistributeStart(packet: FileDistributeStartPacket) {
        // Если файл уже есть в кеше — пропускаем
        if (FileCache.exists(packet.category, packet.fileId, packet.extension)) {
            logger.debug("File already cached: ${packet.fileId}.${packet.extension}, skipping")
            return
        }

        val session = DownloadSession(
            sessionId = packet.sessionId,
            fileId = packet.fileId,
            category = packet.category,
            extension = packet.extension,
            totalSize = packet.totalSize,
            totalChunks = packet.totalChunks,
            startedAt = System.currentTimeMillis(),
            timeoutMs = FileTransferConfig.calculateTimeout(packet.totalSize)
        )

        downloadSessions[packet.sessionId] = session
        logger.info("Download started: ${packet.fileId}.${packet.extension} " +
                    "(${packet.totalSize} bytes, ${packet.totalChunks} chunks)")
    }

    /**
     * Получен чанк файла от сервера.
     */
    private fun onDistributeChunk(packet: FileDistributeChunkPacket) {
        val session = downloadSessions[packet.sessionId] ?: return

        session.receivedChunks[packet.chunkIndex] = packet.data

        if (session.isComplete) {
            downloadSessions.remove(packet.sessionId)
            logger.info("Download complete: ${session.fileId}.${session.extension}")

            // Сборка и запись — в IO поток
            ioScope.launch {
                try {
                    val bytes = session.assembleBytes()
                    FileCache.save(session.category, session.fileId, session.extension, bytes)

                    // Уведомляем подписчиков на main thread
                    withContext(Dispatchers.Main) {
                        notifyHandlers(session.category, session.fileId, bytes, session.extension)
                    }
                } catch (e: Exception) {
                    logger.error("Failed to process downloaded file ${session.fileId}", e)
                }
            }
        }
    }

    // ── Уведомление обработчиков ──────────────────────────────────────────

    private fun notifyHandlers(
        category: FileCategory,
        fileId: String,
        bytes: ByteArray,
        extension: String
    ) {
        fileHandlers[category]?.forEach { handler ->
            try {
                handler(fileId, bytes, extension)
            } catch (e: Exception) {
                logger.error("Error in client file handler for $fileId", e)
            }
        }
    }

    // ── Очистка истёкших сессий ───────────────────────────────────────────

    private fun startSessionCleanup() {
        ioScope.launch {
            while (true) {
                delay(FileTransferConfig.CLEANUP_INTERVAL_MS)
                val now = System.currentTimeMillis()

                val timedOut = downloadSessions.entries
                    .filter { it.value.isTimedOut(now) }
                    .map { it.key }

                if (timedOut.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        timedOut.forEach { sessionId ->
                            val session = downloadSessions.remove(sessionId)
                            if (session != null) {
                                logger.warn("Download session timed out: ${session.fileId}")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Обработчик получения файла на клиенте: (fileId, bytes, extension) → Unit */
typealias ClientFileReceivedHandler = (fileId: String, bytes: ByteArray, extension: String) -> Unit


