package omc.boundbyfate.event.core

/**
 * Интерфейс для событий, которые можно отменить.
 * 
 * Отменяемые события позволяют обработчикам предотвратить
 * выполнение действия (например, отменить атаку, блокировать урон).
 * 
 * Пример:
 * ```kotlin
 * val event = DamageEvent(target, amount)
 * BbfEvents.Combat.BEFORE_DAMAGE.invoker().onBeforeDamage(event)
 * 
 * if (event.isCancelled) {
 *     return // Урон отменён
 * }
 * ```
 */
interface CancellableEvent {
    /**
     * Флаг отмены события.
     */
    var isCancelled: Boolean
    
    /**
     * Отменяет событие.
     */
    fun cancel() {
        isCancelled = true
    }
    
    /**
     * Проверяет, отменено ли событие.
     */
    fun isCancelled(): Boolean = isCancelled
}

/**
 * Базовая реализация отменяемого события.
 */
abstract class BaseCancellableEvent : CancellableEvent {
    override var isCancelled: Boolean = false
}
