package omc.boundbyfate.system.transfer

import kotlinx.coroutines.*
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.network.core.PacketSender
import omc.boundbyfate.network.extension.sendPacket
import omc.boundbyfate.network.packet.c2s.FileUploadCancelPacket
import omc.boundbyfate.network.packet.c2s.FileUploadChunkPacket
import omc.boundbyfate.network.packet.c2s.FileUploadStartPacket
import omc.boundbyfate.network.packet.s2c.FileDistributeChunkPacket
import omc.boundbyfate.network.packet.s2c.FileDistributeStartPacket
import omc.boundbyfate.network.packet.s2c.FileSyncListPacket
import omc.boundbyfate.network.packet.s2c.FileUploadAckPacket
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Серверная система передачи файлов.
 *
 * Принимает файлы от ГМ (C2S) и раздаёт их всем клиентам (S2C).
 * Тяжёлые IO операции выполняются асинхронно в [Dispatchers.IO].
 * Состояние сессий изменяется только на server thread.
 *
 * ## Использование
 *
 * ```kotlin
 * // Подписаться на получение файла определённой категории
 * FileTransferSystem.onFileReceived(FileCategory.MUSIC) { fileId, bytes, extension ->
 *     MusicSystem.onTrackReceived(fileId, bytes, extension)
 * }
 * ```
 */
object FileTransferSystem {

    private val logger = LoggerFactory.getLogger(FileTransferSystem::class.java)

    /** Coroutine scope для IO операций. SupervisorJob — ошибка одной корутины не убивает остальные. */
    private val ioScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineName("FileTransfer-IO")
    )

    /** Активные сессии загрузки: sessionId → сессия. */
    private val uploadSessions = ConcurrentHashMap<UUID, UploadSession>()

    /** Обработчики получения файлов: категория → список обработчиков. */
    private val fileHandlers = ConcurrentHashMap<FileCategory, MutableList<FileReceivedHandler>>()

    /** Ссылка на сервер — нужна для отправки пакетов игрокам. */
    private lateinit var server: MinecraftServer

    // ── Инициализация ─────────────────────────────────────────────────────

    /**
     * Регистрирует систему.
     * Вызывается при инициализации мода.
     */
    fun register() {
        // При старте сервера — инициализируем хранилище
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register { srv ->
            server = srv
            FileStorage.init(srv)
            startSessionCleanup()
            logger.info("FileTransferSystem started")
        }

        // При подключении игрока — отправляем список файлов
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            sendFileListToPlayer(handler.player)
        }

        logger.info("FileTransferSystem registered")
    }

    // ── Подписка на события ───────────────────────────────────────────────

    /**
     * Регистрирует обработчик получения файла.
     *
     * ```kotlin
     * FileTransferSystem.onFileReceived(FileCategory.MUSIC) { fileId, bytes, extension ->
     *     MusicSystem.onTrackReceived(fileId, bytes, extension)
     * }
     * ```
     */
    fun onFileReceived(category: FileCategory, handler: FileReceivedHandler) {
        fileHandlers.getOrPut(category) { mutableListOf() }.add(handler)
    }

    // ── Обработка входящих пакетов (вызывается на server thread) ──────────

    /**
     * ГМ начинает загрузку файла.
     */
    fun onUploadStart(packet: FileUploadStartPacket, sender: ServerPlayerEntity) {
        // Проверка прав — только оператор
        if (!sender.hasPermissionLevel(2)) {
            logger.warn("Non-operator ${sender.name.string} tried to upload a file")
            return
        }

        // Проверка размера
        if (packet.totalSize > FileTransferConfig.MAX_FILE_SIZE_BYTES) {
            logger.warn("File too large: ${packet.totalSize} bytes (max ${FileTransferConfig.MAX_FILE_SIZE_BYTES})")
            sender.sendPacket(FileUploadAckPacket(
                sessionId = packet.sessionId,
                chunkIndex = -1,
                success = false,
                errorMessage = "File too large (max ${FileTransferConfig.MAX_FILE_SIZE_BYTES / 1024 / 1024} MB)"
            ))
            return
        }

        // Проверка количества одновременных загрузок
        val activeUploads = uploadSessions.values.count()
        if (activeUploads >= FileTransferConfig.MAX_CONCURRENT_UPLOADS) {
            logger.warn("Too many concurrent uploads ($activeUploads)")
            sender.sendPacket(FileUploadAckPacket(
                sessionId = packet.sessionId,
                chunkIndex = -1,
                success = false,
                errorMessage = "Too many concurrent uploads"
            ))
            return
        }

        val session = UploadSession(
            sessionId = packet.sessionId,
            fileId = packet.fileId,
            category = packet.category,
            extension = packet.extension,
            totalSize = packet.totalSize,
            totalChunks = packet.totalChunks,
            startedAt = System.currentTimeMillis(),
            timeoutMs = FileTransferConfig.calculateTimeout(packet.totalSize)
        )

        uploadSessions[packet.sessionId] = session
        logger.info("Upload started: ${packet.fileId}.${packet.extension} " +
                    "(${packet.totalSize} bytes, ${packet.totalChunks} chunks) " +
                    "timeout=${session.timeoutMs}ms")

        // Подтверждаем начало загрузки
        sender.sendPacket(FileUploadAckPacket(packet.sessionId, -1, success = true))
    }

    /**
     * Получен чанк файла от ГМ.
     */
    fun onUploadChunk(packet: FileUploadChunkPacket, sender: ServerPlayerEntity) {
        val session = uploadSessions[packet.sessionId]
        if (session == null) {
            sender.sendPacket(FileUploadAckPacket(
                sessionId = packet.sessionId,
                chunkIndex = packet.chunkIndex,
                success = false,
                errorMessage = "Session not found"
            ))
            return
        }

        // Сохраняем чанк
        session.receivedChunks[packet.chunkIndex] = packet.data

        // Подтверждаем получение
        sender.sendPacket(FileUploadAckPacket(packet.sessionId, packet.chunkIndex, success = true))

        // Все чанки получены?
        if (session.isComplete) {
            uploadSessions.remove(packet.sessionId)
            logger.info("Upload complete: ${session.fileId}.${session.extension}")

            // Тяжёлая работа — в IO поток
            ioScope.launch {
                try {
                    val bytes = session.assembleBytes()

                    // Сохраняем на диск
                    FileStorage.save(session.category, session.fileId, session.extension, bytes)

                    // Возвращаемся на server thread для отправки пакетов
                    withContext(Dispatchers.Main) {
                        distributeToAllClients(session, bytes)
                        notifyHandlers(session.category, session.fileId, bytes, session.extension)
                    }
                } catch (e: Exception) {
                    logger.error("Failed to process uploaded file ${session.fileId}", e)
                }
            }
        }
    }

    /**
     * ГМ отменил загрузку.
     */
    fun onUploadCancel(packet: FileUploadCancelPacket, sender: ServerPlayerEntity) {
        val removed = uploadSessions.remove(packet.sessionId)
        if (removed != null) {
            logger.info("Upload cancelled: ${removed.fileId} by ${sender.name.string}")
        }
    }

    // ── Удаление файла ────────────────────────────────────────────────────

    /**
     * ГМ удаляет файл с сервера.
     * Уведомляет клиентов об удалении.
     */
    fun deleteFile(
        category: FileCategory,
        fileId: String,
        extension: String,
        requester: ServerPlayerEntity
    ) {
        if (!requester.hasPermissionLevel(2)) return

        ioScope.launch {
            val deleted = FileStorage.delete(category, fileId, extension)
            if (deleted) {
                logger.info("File deleted: $fileId.$extension by ${requester.name.string}")
                // TODO: уведомить клиентов об удалении (FileDeletedPacket — добавить при необходимости)
            }
        }
    }

    // ── Раздача файлов клиентам ───────────────────────────────────────────

    /**
     * Раздаёт файл всем подключённым клиентам.
     * Вызывается на server thread после сохранения файла.
     */
    private fun distributeToAllClients(session: UploadSession, bytes: ByteArray) {
        val players = server.playerManager.playerList
        if (players.isEmpty()) return

        val sessionId = UUID.randomUUID()
        val chunks = bytes.toChunks(FileTransferConfig.CHUNK_SIZE_BYTES)

        val startPacket = FileDistributeStartPacket(
            sessionId = sessionId,
            fileId = session.fileId,
            category = session.category,
            extension = session.extension,
            totalSize = bytes.size.toLong(),
            totalChunks = chunks.size
        )

        players.forEach { it.sendPacket(startPacket) }

        // Отправляем чанки асинхронно чтобы не блокировать server thread
        ioScope.launch {
            chunks.forEachIndexed { index, chunk ->
                val chunkPacket = FileDistributeChunkPacket(sessionId, index, chunk)
                withContext(Dispatchers.Main) {
                    players.forEach { it.sendPacket(chunkPacket) }
                }
                // Небольшая пауза между чанками чтобы не перегружать сеть
                delay(5)
            }
            logger.info("Distributed ${session.fileId}.${session.extension} to ${players.size} players")
        }
    }

    /**
     * Отправляет конкретный файл одному игроку (при подключении).
     */
    fun distributeFileToPlayer(
        player: ServerPlayerEntity,
        fileId: String,
        category: FileCategory,
        extension: String
    ) {
        ioScope.launch {
            val bytes = FileStorage.load(category, fileId, extension) ?: return@launch
            val sessionId = UUID.randomUUID()
            val chunks = bytes.toChunks(FileTransferConfig.CHUNK_SIZE_BYTES)

            val startPacket = FileDistributeStartPacket(
                sessionId = sessionId,
                fileId = fileId,
                category = category,
                extension = extension,
                totalSize = bytes.size.toLong(),
                totalChunks = chunks.size
            )

            withContext(Dispatchers.Main) { player.sendPacket(startPacket) }

            chunks.forEachIndexed { index, chunk ->
                val chunkPacket = FileDistributeChunkPacket(sessionId, index, chunk)
                withContext(Dispatchers.Main) { player.sendPacket(chunkPacket) }
                delay(5)
            }
        }
    }

    /**
     * Отправляет новому игроку список всех файлов на сервере.
     * Клиент сам запросит те которых нет в кеше.
     */
    private fun sendFileListToPlayer(player: ServerPlayerEntity) {
        ioScope.launch {
            val files = FileStorage.listAllFiles().map { info ->
                FileSyncListPacket.FileMetadata(
                    fileId = info.fileId,
                    category = info.category,
                    extension = info.extension,
                    totalSize = info.totalSize
                )
            }

            if (files.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    player.sendPacket(FileSyncListPacket(files))
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
                logger.error("Error in file handler for $fileId", e)
            }
        }
    }

    // ── Очистка истёкших сессий ───────────────────────────────────────────

    private fun startSessionCleanup() {
        ioScope.launch {
            while (true) {
                delay(FileTransferConfig.CLEANUP_INTERVAL_MS)
                val now = System.currentTimeMillis()

                val timedOut = uploadSessions.entries
                    .filter { it.value.isTimedOut(now) }
                    .map { it.key }

                if (timedOut.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        timedOut.forEach { sessionId ->
                            val session = uploadSessions.remove(sessionId)
                            if (session != null) {
                                logger.warn(
                                    "Upload session timed out: ${session.fileId} " +
                                    "(received ${session.receivedCount}/${session.totalChunks} chunks)"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Shutdown ──────────────────────────────────────────────────────────

    /**
     * Останавливает систему при выключении сервера.
     */
    fun shutdown() {
        ioScope.cancel()
        logger.info("FileTransferSystem shutdown")
    }
}

// ── Типы ──────────────────────────────────────────────────────────────────

/** Обработчик получения файла: (fileId, bytes, extension) → Unit */
typealias FileReceivedHandler = (fileId: String, bytes: ByteArray, extension: String) -> Unit

// ── Extension ─────────────────────────────────────────────────────────────

/** Разбивает ByteArray на чанки заданного размера. */
private fun ByteArray.toChunks(chunkSize: Int): List<ByteArray> {
    val chunks = mutableListOf<ByteArray>()
    var offset = 0
    while (offset < size) {
        val end = minOf(offset + chunkSize, size)
        chunks.add(copyOfRange(offset, end))
        offset = end
    }
    return chunks
}
