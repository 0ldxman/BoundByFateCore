package omc.boundbyfate.registry.core

import net.minecraft.util.Identifier
import omc.boundbyfate.api.core.Registrable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Базовый generic класс для всех регистров в системе.
 * 
 * Registry хранит Definition (правила игры) и предоставляет
 * типобезопасный доступ к ним по ID.
 * 
 * @param T тип регистрируемых объектов (должен быть Registrable)
 */
abstract class BbfRegistry<T : Registrable>(
    /**
     * Уникальное имя регистра (например, "stats", "classes", "races")
     */
    val name: String
) {
    private val logger: Logger = LoggerFactory.getLogger("BbfRegistry[$name]")
    
    /**
     * Внутреннее хранилище зарегистрированных объектов.
     * Thread-safe для безопасной работы в многопоточной среде.
     */
    private val entries: MutableMap<Identifier, T> = ConcurrentHashMap()
    
    /**
     * Регистрирует объект в регистре.
     * 
     * @param entry объект для регистрации
     * @throws IllegalStateException если объект с таким ID уже зарегистрирован
     */
    fun register(entry: T) {
        // Валидация перед регистрацией
        entry.validate()
        
        // Проверка на дубликаты
        if (entries.containsKey(entry.id)) {
            throw IllegalStateException(
                "Duplicate registration in $name: ${entry.id} is already registered"
            )
        }
        
        entries[entry.id] = entry
        logger.debug("Registered ${entry.id} in $name")
    }
    
    /**
     * Получает объект по ID.
     * 
     * @param id идентификатор объекта
     * @return объект или null если не найден
     */
    fun get(id: Identifier): T? = entries[id]
    
    /**
     * Получает объект по ID или выбрасывает исключение.
     * 
     * @param id идентификатор объекта
     * @return объект
     * @throws NoSuchElementException если объект не найден
     */
    fun getOrThrow(id: Identifier): T =
        get(id) ?: throw NoSuchElementException("$id not found in $name registry")
    
    /**
     * Проверяет, зарегистрирован ли объект с данным ID.
     */
    fun contains(id: Identifier): Boolean = entries.containsKey(id)
    
    /**
     * Возвращает все зарегистрированные объекты.
     */
    fun getAll(): Collection<T> = entries.values
    
    /**
     * Возвращает все ID зарегистрированных объектов.
     */
    fun getAllIds(): Set<Identifier> = entries.keys
    
    /**
     * Возвращает количество зарегистрированных объектов.
     */
    fun size(): Int = entries.size
    
    /**
     * Очищает регистр (используется для перезагрузки).
     */
    fun clear() {
        entries.clear()
        logger.info("Cleared $name registry")
    }
    
    /**
     * Callback вызываемый после регистрации всех объектов.
     * Используется для пост-обработки (например, разрешение ссылок).
     */
    open fun onRegistrationComplete() {
        logger.info("Registration complete for $name: ${size()} entries")
    }
}
