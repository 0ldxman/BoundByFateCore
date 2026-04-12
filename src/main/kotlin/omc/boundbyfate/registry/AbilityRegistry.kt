package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.ability.AbilityDefinition
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Реестр способностей.
 * 
 * Хранит все загруженные способности (заклинания и классовые способности).
 * Загружается из JSON датапаков при старте сервера.
 */
object AbilityRegistry {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")
    private val abilities = ConcurrentHashMap<Identifier, AbilityDefinition>()
    
    /**
     * Регистрирует способность.
     */
    fun register(ability: AbilityDefinition) {
        abilities[ability.id] = ability
        logger.debug("Registered ability: ${ability.id}")
    }
    
    /**
     * Получает способность по ID.
     */
    fun get(id: Identifier): AbilityDefinition? = abilities[id]
    
    /**
     * Получает все зарегистрированные способности.
     */
    fun getAll(): Collection<AbilityDefinition> = abilities.values
    
    /**
     * Получает все способности, соответствующие предикату.
     */
    fun filter(predicate: (AbilityDefinition) -> Boolean): List<AbilityDefinition> =
        abilities.values.filter(predicate)
    
    /**
     * Проверяет, зарегистрирована ли способность.
     */
    fun contains(id: Identifier): Boolean = abilities.containsKey(id)
    
    /**
     * Очищает реестр (для перезагрузки датапаков).
     */
    fun clear() {
        abilities.clear()
        logger.info("Cleared ability registry")
    }
    
    /**
     * Возвращает количество зарегистрированных способностей.
     */
    fun size(): Int = abilities.size
}
