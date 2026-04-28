package omc.boundbyfate.system.transfer

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Сессия получения файла клиентом от сервера.
 *
 * Создаётся при получении [FileDistributeStartPacket].
 *
 * @param sessionId уникальный ID сессии
 * @param fileId ID файла
 * @param category категория файла
 * @param extension расширение файла
 * @param totalSize полный размер файла в байтах
 * @param totalChunks общее количество чанков
 * @param startedAt время начала получения
 * @param timeoutMs таймаут сессии
 */
data class DownloadSession(
    val sessionId: UUID,
    val fileId: String,
    val category: FileCategory,
    val extension: String,
    val totalSize: Long,
    val totalChunks: Int,
    val startedAt: Long,
    val timeoutMs: Long
) {
    /** Полученные чанки: индекс → байты. Thread-safe. */
    val receivedChunks: ConcurrentHashMap<Int, ByteArray> = ConcurrentHashMap()

    val isComplete: Boolean get() = receivedChunks.size == totalChunks

    fun isTimedOut(now: Long): Boolean = now - startedAt > timeoutMs

    /**
     * Собирает все чанки в единый массив байт.
     */
    fun assembleBytes(): ByteArray {
        val result = ByteArray(totalSize.toInt())
        var offset = 0
        for (i in 0 until totalChunks) {
            val chunk = receivedChunks[i]
                ?: error("Missing chunk $i in session $sessionId")
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        return result
    }
}
