package omc.boundbyfate.event.core

/**
 * Интерфейс для событий с изменяемым результатом.
 * 
 * Result события позволяют обработчикам изменять результат действия
 * (например, изменить количество урона, модифицировать дроп).
 * 
 * @param T тип результата
 * 
 * Пример:
 * ```kotlin
 * val event = DamageEvent(target, amount = 10)
 * BbfEvents.Combat.CALCULATE_DAMAGE.invoker().onCalculateDamage(event)
 * 
 * val finalDamage = event.result // Может быть изменён обработчиками
 * ```
 */
interface ResultEvent<T> {
    /**
     * Текущий результат события.
     * Может быть изменён обработчиками.
     */
    var result: T
    
    /**
     * Исходный результат (до изменений).
     * Неизменяемый, для справки.
     */
    val originalResult: T
}

/**
 * Базовая реализация события с результатом.
 */
abstract class BaseResultEvent<T>(
    initialResult: T
) : ResultEvent<T> {
    override var result: T = initialResult
    override val originalResult: T = initialResult
}

/**
 * Событие с результатом, которое также можно отменить.
 */
abstract class CancellableResultEvent<T>(
    initialResult: T
) : BaseResultEvent<T>(initialResult), CancellableEvent {
    override var isCancelled: Boolean = false
}
