package omc.boundbyfate.system.transfer

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Сессия загрузки файла с клиента ГМ на сервер.
 *
 * Создаётся при получении [FileUploadStartPacket], удаляется когда
 * все чанки получены или истёк таймаут.
 *
 * @param sessionId уникальный ID сессии
 * @param fileId желаемый ID файла (задаётся ГМ-ом)
 * @param category категория файла
 * @param extension расширение файла ("ogg", "mp3", "wav", "png" и т.д.)
 * @param totalSize полный размер файла в байтах
 * @param totalChunks общее количество чанков
 * @param startedAt время начала загрузки (System.currentTimeMillis())
 * @param timeoutMs таймаут сессии в миллисекундах (динамический)
 */
data class UploadSession(
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

    /** Количество полученных чанков. */
    val receivedCount: Int get() = receivedChunks.size

    /** Все ли чанки получены. */
    val isComplete: Boolean get() = receivedChunks.size == totalChunks

    /** Истёк ли таймаут. */
    fun isTimedOut(now: Long): Boolean = now - startedAt > timeoutMs

    /**
     * Собирает все чанки в единый массив байт.
     * Вызывать только когда [isComplete] == true.
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
