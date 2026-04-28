package omc.boundbyfate.event.core

/**
 * Интерфейс для событий, которые можно отменить.
 */
interface CancellableEvent {
    var isCancelled: Boolean

    fun cancel() {
        isCancelled = true
    }
}

/**
 * Базовая реализация отменяемого события.
 */
abstract class BaseCancellableEvent : CancellableEvent {
    override var isCancelled: Boolean = false
}
