package omc.boundbyfate.system.transfer

/**
 * Конфигурация системы передачи файлов.
 */
object FileTransferConfig {

    /** Максимальный размер файла в байтах (20 МБ). */
    const val MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024L

    /** Размер одного чанка в байтах (32 КБ). */
    const val CHUNK_SIZE_BYTES = 32 * 1024

    /** Минимальный таймаут сессии в миллисекундах (60 секунд). */
    const val MIN_SESSION_TIMEOUT_MS = 60_000L

    /**
     * Минимальная ожидаемая скорость передачи в байтах/сек (256 КБ/с).
     * Используется для расчёта динамического таймаута.
     * Намеренно занижена чтобы дать запас при плохом соединении.
     */
    const val MIN_EXPECTED_SPEED_BYTES_PER_SEC = 256 * 1024L

    /** Интервал проверки истёкших сессий в миллисекундах. */
    const val CLEANUP_INTERVAL_MS = 30_000L

    /** Максимальное количество одновременных загрузок от одного ГМ. */
    const val MAX_CONCURRENT_UPLOADS = 3

    /**
     * Вычисляет динамический таймаут для сессии на основе размера файла.
     *
     * Формула: max(MIN_TIMEOUT, ожидаемое_время × 3)
     * Множитель 3 даёт трёхкратный запас относительно минимальной скорости.
     *
     * @param fileSizeBytes размер файла в байтах
     * @return таймаут в миллисекундах
     */
    fun calculateTimeout(fileSizeBytes: Long): Long {
        val expectedMs = (fileSizeBytes.toDouble() / MIN_EXPECTED_SPEED_BYTES_PER_SEC * 1000).toLong()
        return maxOf(MIN_SESSION_TIMEOUT_MS, expectedMs * 3)
    }
}
