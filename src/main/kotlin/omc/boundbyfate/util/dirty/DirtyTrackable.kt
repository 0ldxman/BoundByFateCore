package omc.boundbyfate.util.dirty

/**
 * Интерфейс для объектов, которые отслеживают изменения (dirty tracking).
 * 
 * Dirty tracking позволяет:
 * - Избежать лишних пересчётов (пересчитываем только когда isDirty = true)
 * - Оптимизировать синхронизацию с клиентом (отправляем только изменённые данные)
 * - Кэшировать вычисленные значения
 * 
 * Философия:
 * - Объект помечается "грязным" (dirty) когда изменяются его данные
 * - При обращении к вычисленному значению проверяется isDirty
 * - Если isDirty = true → пересчёт → markClean()
 * - Если isDirty = false → возврат кэшированного значения
 * 
 * Пример использования:
 * ```kotlin
 * data class EntityStatData(
 *     private val baseStats: Map<Identifier, Int>,
 *     private val modifiers: List<StatModifier>,
 *     private val computed: Map<Identifier, Int> = emptyMap(),
 *     private val dirty: Boolean = true
 * ) : DirtyTrackable {
 *     
 *     override fun isDirty(): Boolean = dirty
 *     
 *     fun getStat(statId: Identifier): Int {
 *         if (isDirty()) {
 *             // Пересчитываем все статы
 *             val newComputed = recalculateAll()
 *             return copy(computed = newComputed, dirty = false)
 *         }
 *         return computed[statId]!!
 *     }
 *     
 *     fun addModifier(modifier: StatModifier): EntityStatData {
 *         return copy(
 *             modifiers = modifiers + modifier,
 *             dirty = true  // Помечаем как грязный
 *         )
 *     }
 * }
 * ```
 */
interface DirtyTrackable {
    /**
     * Проверяет, изменились ли данные с момента последнего пересчёта.
     * 
     * @return true если данные изменились и нужен пересчёт
     */
    fun isDirty(): Boolean
    
    /**
     * Помечает объект как "грязный" (требуется пересчёт).
     * 
     * Вызывается когда:
     * - Изменяются базовые значения
     * - Добавляются/удаляются модификаторы
     * - Изменяются зависимости
     */
    fun markDirty(): DirtyTrackable
    
    /**
     * Помечает объект как "чистый" (пересчёт выполнен).
     * 
     * Вызывается после:
     * - Пересчёта всех вычисленных значений
     * - Обновления кэша
     */
    fun markClean(): DirtyTrackable
}

/**
 * Простая реализация DirtyTrackable через делегирование.
 * 
 * Используется для добавления dirty tracking к существующим классам.
 * 
 * Пример:
 * ```kotlin
 * data class MyComponent(
 *     val data: String,
 *     private val dirtyState: DirtyState = DirtyState()
 * ) : DirtyTrackable by dirtyState {
 *     
 *     fun updateData(newData: String): MyComponent {
 *         return copy(
 *             data = newData,
 *             dirtyState = dirtyState.markDirty() as DirtyState
 *         )
 *     }
 * }
 * ```
 */
data class DirtyState(
    private val dirty: Boolean = true
) : DirtyTrackable {
    
    override fun isDirty(): Boolean = dirty
    
    override fun markDirty(): DirtyState = copy(dirty = true)
    
    override fun markClean(): DirtyState = copy(dirty = false)
}

/**
 * Extension функция для удобной работы с DirtyTrackable.
 * 
 * Автоматически пересчитывает значение если объект dirty.
 * 
 * Пример:
 * ```kotlin
 * val stat = statData.computeIfDirty { data ->
 *     // Пересчёт происходит только если isDirty = true
 *     recalculateStats(data)
 * }
 * ```
 */
inline fun <T : DirtyTrackable, R> T.computeIfDirty(compute: (T) -> Pair<R, T>): Pair<R, T> {
    return if (isDirty()) {
        val (result, cleaned) = compute(this)
        result to cleaned.markClean() as T
    } else {
        throw IllegalStateException("Cannot compute on clean object - value should be cached")
    }
}
