package omc.boundbyfate.event.core

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Улучшенная система событий с поддержкой приоритетов.
 * 
 * Предоставляет более гибкий API чем стандартный Fabric EventFactory:
 * - Приоритеты выполнения
 * - Динамическая регистрация/отписка
 * - Типобезопасность
 * - Performance оптимизации
 * 
 * @param T тип обработчика события
 */
class EventBus<T : Any>(
    /**
     * Имя события (для логирования и отладки).
     */
    val name: String,
    
    /**
     * Класс обработчика (для типобезопасности).
     */
    private val handlerClass: Class<T>
) {
    private val logger = LoggerFactory.getLogger("EventBus[$name]")
    
    /**
     * Зарегистрированные обработчики с приоритетами.
     * Используем ConcurrentHashMap для thread-safety.
     */
    internal val handlers: MutableMap<EventPriority, MutableList<T>> = ConcurrentHashMap()
    
    /**
     * Кэшированный отсортированный список обработчиков.
     * Пересоздаётся при регистрации нового обработчика.
     */
    @Volatile
    private var cachedHandlers: List<T>? = null
    
    init {
        // Инициализируем списки для каждого приоритета
        EventPriority.values().forEach { priority ->
            handlers[priority] = mutableListOf()
        }
    }
    
    /**
     * Регистрирует обработчик события с приоритетом.
     * 
     * @param priority приоритет выполнения
     * @param handler обработчик
     */
    fun register(priority: EventPriority = EventPriority.NORMAL, handler: T) {
        handlers[priority]?.add(handler)
        cachedHandlers = null // Инвалидируем кэш
        
        logger.debug("Registered handler for $name with priority $priority")
    }
    
    /**
     * Отписывает обработчик от события.
     * 
     * @param handler обработчик для удаления
     * @return true если обработчик был найден и удалён
     */
    fun unregister(handler: T): Boolean {
        var removed = false
        
        for (list in handlers.values) {
            if (list.remove(handler)) {
                removed = true
            }
        }
        
        if (removed) {
            cachedHandlers = null // Инвалидируем кэш
            logger.debug("Unregistered handler from $name")
        }
        
        return removed
    }
    
    /**
     * Получает все обработчики в порядке приоритета.
     * Использует кэширование для оптимизации.
     */
    fun getHandlers(): List<T> {
        // Проверяем кэш
        cachedHandlers?.let { return it }
        
        // Создаём отсортированный список
        val sorted = mutableListOf<T>()
        
        for (priority in EventPriority.values()) {
            handlers[priority]?.let { sorted.addAll(it) }
        }
        
        // Кэшируем результат
        cachedHandlers = sorted
        return sorted
    }
    
    /**
     * Вызывает все обработчики события.
     * 
     * @param invoker функция вызова обработчика
     */
    inline fun invoke(invoker: (T) -> Unit) {
        for (handler in getHandlers()) {
            try {
                invoker(handler)
            } catch (e: Exception) {
                logger.error("Error invoking handler for $name", e)
            }
        }
    }
    
    /**
     * Вызывает обработчики с поддержкой отмены.
     * Останавливается если событие отменено (кроме MONITOR).
     * 
     * @param event отменяемое событие
     * @param invoker функция вызова обработчика
     */
    inline fun invokeCancellable(
        event: CancellableEvent,
        invoker: (T) -> Unit
    ) {
        for (priority in EventPriority.values()) {
            // Пропускаем если событие отменено (кроме MONITOR)
            if (event.isCancelled && priority != EventPriority.MONITOR) {
                continue
            }
            
            handlers[priority]?.forEach { handler ->
                try {
                    invoker(handler)
                } catch (e: Exception) {
                    logger.error("Error invoking handler for $name", e)
                }
            }
        }
    }
    
    /**
     * Возвращает количество зарегистрированных обработчиков.
     */
    fun size(): Int = handlers.values.sumOf { it.size }
    
    /**
     * Очищает все обработчики.
     */
    fun clear() {
        handlers.values.forEach { it.clear() }
        cachedHandlers = null
        logger.info("Cleared all handlers for $name")
    }
}

/**
 * Builder для создания EventBus.
 */
inline fun <reified T : Any> eventBus(name: String): EventBus<T> =
    EventBus(name, T::class.java)
