package omc.boundbyfate.registry.core

import omc.boundbyfate.api.core.Registrable
import org.slf4j.LoggerFactory

/**
 * Центральный менеджер для управления всеми регистрами в системе.
 * 
 * Отвечает за:
 * - Регистрацию новых регистров
 * - Доступ к регистрам по имени
 * - Lifecycle management (инициализация, перезагрузка)
 */
object RegistryManager {
    private val logger = LoggerFactory.getLogger(RegistryManager::class.java)
    
    /**
     * Все зарегистрированные регистры.
     */
    private val registries: MutableMap<String, BbfRegistry<*>> = LinkedHashMap()
    
    /**
     * Регистрирует новый регистр в системе.
     * 
     * @param registry регистр для добавления
     * @throws IllegalStateException если регистр с таким именем уже существует
     */
    fun <T : Registrable> registerRegistry(registry: BbfRegistry<T>) {
        if (registries.containsKey(registry.name)) {
            throw IllegalStateException("Registry ${registry.name} is already registered")
        }
        
        registries[registry.name] = registry
        logger.info("Registered registry: ${registry.name}")
    }
    
    /**
     * Получает регистр по имени.
     * 
     * @param name имя регистра
     * @return регистр или null если не найден
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Registrable> getRegistry(name: String): BbfRegistry<T>? =
        registries[name] as? BbfRegistry<T>
    
    /**
     * Получает регистр по имени или выбрасывает исключение.
     * 
     * @param name имя регистра
     * @return регистр
     * @throws NoSuchElementException если регистр не найден
     */
    fun <T : Registrable> getRegistryOrThrow(name: String): BbfRegistry<T> =
        getRegistry(name) ?: throw NoSuchElementException("Registry $name not found")
    
    /**
     * Возвращает все зарегистрированные регистры.
     */
    fun getAllRegistries(): Collection<BbfRegistry<*>> = registries.values
    
    /**
     * Вызывает onRegistrationComplete() для всех регистров.
     * Используется после загрузки всех данных из JSON.
     */
    fun finalizeRegistration() {
        logger.info("Finalizing registration for all registries...")
        
        for (registry in registries.values) {
            registry.onRegistrationComplete()
        }
        
        logger.info("Registration finalized for ${registries.size} registries")
    }
    
    /**
     * Очищает все регистры (для перезагрузки).
     */
    fun clearAll() {
        logger.info("Clearing all registries...")
        
        for (registry in registries.values) {
            registry.clear()
        }
        
        logger.info("All registries cleared")
    }
    
    /**
     * Выводит статистику по всем регистрам.
     */
    fun printStatistics() {
        logger.info("=== Registry Statistics ===")
        
        for (registry in registries.values) {
            logger.info("  ${registry.name}: ${registry.size()} entries")
        }
        
        logger.info("===========================")
    }
}
