package omc.boundbyfate.util.time

/**
 * Утилиты для работы со временем и длительностью.
 * 
 * В Minecraft:
 * - 1 тик = 50 миллисекунд (20 тиков в секунду)
 * - 1 секунда = 20 тиков
 * - 1 минута = 1200 тиков
 * - 1 час = 72000 тиков
 * 
 * Используется для:
 * - Конвертации между тиками/секундами/минутами
 * - Проверки истечения временных эффектов
 * - Работы с кулдаунами
 * - Отслеживания длительности способностей
 */
object TimeUtil {
    
    /**
     * Количество тиков в секунде.
     */
    const val TICKS_PER_SECOND = 20
    
    /**
     * Количество тиков в минуте.
     */
    const val TICKS_PER_MINUTE = TICKS_PER_SECOND * 60 // 1200
    
    /**
     * Количество тиков в часе.
     */
    const val TICKS_PER_HOUR = TICKS_PER_MINUTE * 60 // 72000
    
    /**
     * Количество миллисекунд в тике.
     */
    const val MILLIS_PER_TICK = 50L
    
    // ========== Конвертация ==========
    
    /**
     * Конвертирует секунды в тики.
     */
    fun secondsToTicks(seconds: Int): Int = seconds * TICKS_PER_SECOND
    
    /**
     * Конвертирует секунды в тики (float).
     */
    fun secondsToTicks(seconds: Float): Int = (seconds * TICKS_PER_SECOND).toInt()
    
    /**
     * Конвертирует минуты в тики.
     */
    fun minutesToTicks(minutes: Int): Int = minutes * TICKS_PER_MINUTE
    
    /**
     * Конвертирует часы в тики.
     */
    fun hoursToTicks(hours: Int): Int = hours * TICKS_PER_HOUR
    
    /**
     * Конвертирует тики в секунды.
     */
    fun ticksToSeconds(ticks: Int): Float = ticks.toFloat() / TICKS_PER_SECOND
    
    /**
     * Конвертирует тики в минуты.
     */
    fun ticksToMinutes(ticks: Int): Float = ticks.toFloat() / TICKS_PER_MINUTE
    
    /**
     * Конвертирует тики в часы.
     */
    fun ticksToHours(ticks: Int): Float = ticks.toFloat() / TICKS_PER_HOUR
    
    /**
     * Конвертирует тики в миллисекунды.
     */
    fun ticksToMillis(ticks: Int): Long = ticks * MILLIS_PER_TICK
    
    /**
     * Конвертирует миллисекунды в тики.
     */
    fun millisToTicks(millis: Long): Int = (millis / MILLIS_PER_TICK).toInt()
    
    // ========== Проверка истечения ==========
    
    /**
     * Проверяет, истекла ли длительность.
     * 
     * @param startTick тик начала эффекта
     * @param durationTicks длительность в тиках
     * @param currentTick текущий тик
     * @return true если эффект истёк
     */
    fun isExpired(startTick: Long, durationTicks: Int, currentTick: Long): Boolean {
        return currentTick - startTick >= durationTicks
    }
    
    /**
     * Проверяет, истекла ли длительность (с миллисекундами).
     * 
     * @param startTime время начала в миллисекундах
     * @param durationMillis длительность в миллисекундах
     * @param currentTime текущее время в миллисекундах
     * @return true если эффект истёк
     */
    fun isExpiredMillis(startTime: Long, durationMillis: Long, currentTime: Long): Boolean {
        return currentTime - startTime >= durationMillis
    }
    
    /**
     * Вычисляет оставшееся время в тиках.
     * 
     * @param startTick тик начала эффекта
     * @param durationTicks длительность в тиках
     * @param currentTick текущий тик
     * @return оставшееся время в тиках (0 если истекло)
     */
    fun remainingTicks(startTick: Long, durationTicks: Int, currentTick: Long): Int {
        val elapsed = currentTick - startTick
        return maxOf(0, durationTicks - elapsed.toInt())
    }
    
    /**
     * Вычисляет прогресс длительности (0.0 - 1.0).
     * 
     * @param startTick тик начала эффекта
     * @param durationTicks длительность в тиках
     * @param currentTick текущий тик
     * @return прогресс от 0.0 (начало) до 1.0 (конец)
     */
    fun progress(startTick: Long, durationTicks: Int, currentTick: Long): Float {
        if (durationTicks <= 0) return 1.0f
        val elapsed = currentTick - startTick
        return (elapsed.toFloat() / durationTicks).coerceIn(0.0f, 1.0f)
    }
    
    // ========== Форматирование ==========
    
    /**
     * Форматирует тики в читаемую строку.
     * 
     * Примеры:
     * - 20 тиков → "1s"
     * - 1200 тиков → "1m"
     * - 1220 тиков → "1m 1s"
     * - 72000 тиков → "1h"
     */
    fun formatTicks(ticks: Int): String {
        if (ticks < TICKS_PER_SECOND) {
            return "${ticks}t"
        }
        
        val hours = ticks / TICKS_PER_HOUR
        val minutes = (ticks % TICKS_PER_HOUR) / TICKS_PER_MINUTE
        val seconds = (ticks % TICKS_PER_MINUTE) / TICKS_PER_SECOND
        
        return buildString {
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m ")
            if (seconds > 0) append("${seconds}s")
        }.trim()
    }
    
    /**
     * Форматирует оставшееся время.
     * 
     * @param startTick тик начала
     * @param durationTicks длительность
     * @param currentTick текущий тик
     * @return строка вида "1m 30s" или "expired"
     */
    fun formatRemaining(startTick: Long, durationTicks: Int, currentTick: Long): String {
        val remaining = remainingTicks(startTick, durationTicks, currentTick)
        return if (remaining > 0) formatTicks(remaining) else "expired"
    }
}
