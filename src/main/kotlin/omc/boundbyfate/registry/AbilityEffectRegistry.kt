package omc.boundbyfate.registry

import com.google.gson.JsonObject
import net.minecraft.util.Identifier
import omc.boundbyfate.api.ability.AbilityEffect
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Реестр фабрик для создания эффектов способностей из JSON.
 * 
 * Позволяет регистрировать новые типы эффектов через моддинг.
 * 
 * Пример регистрации:
 * ```kotlin
 * AbilityEffectRegistry.register(Identifier("mymod", "lifesteal")) { json ->
 *     LifestealEffect(
 *         percentage = json.get("percentage")?.asFloat ?: 0.5f,
 *         maxHeal = json.get("maxHeal")?.asInt ?: 20
 *     )
 * }
 * ```
 */
object AbilityEffectRegistry {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")
    private val factories = ConcurrentHashMap<Identifier, EffectFactory>()
    
    /**
     * Регистрирует фабрику для создания эффектов из JSON.
     * 
     * @param id Уникальный идентификатор типа эффекта
     * @param factory Фабрика для создания эффекта из JSON
     */
    fun register(id: Identifier, factory: EffectFactory) {
        if (factories.containsKey(id)) {
            logger.warn("Overwriting effect factory for $id")
        }
        factories[id] = factory
        logger.debug("Registered effect factory: $id")
    }
    
    /**
     * Создаёт эффект из JSON.
     * 
     * @param type Тип эффекта
     * @param params JSON параметры эффекта
     * @return Созданный эффект или null если фабрика не найдена
     */
    fun create(type: Identifier, params: JsonObject): AbilityEffect? {
        val factory = factories[type]
        if (factory == null) {
            logger.error("Unknown effect type: $type")
            return null
        }
        
        return try {
            factory.create(params)
        } catch (e: Exception) {
            logger.error("Failed to create effect $type", e)
            null
        }
    }
    
    /**
     * Получает все зарегистрированные типы эффектов.
     */
    fun getAll(): Collection<Identifier> = factories.keys
    
    /**
     * Проверяет, зарегистрирован ли тип эффекта.
     */
    fun has(type: Identifier): Boolean = factories.containsKey(type)
    
    /**
     * Очищает все зарегистрированные фабрики.
     * Используется для тестирования.
     */
    fun clear() {
        factories.clear()
    }
}

/**
 * Фабрика для создания эффектов из JSON.
 */
fun interface EffectFactory {
    /**
     * Создаёт эффект из JSON параметров.
     * 
     * @param params JSON объект с параметрами эффекта
     * @return Созданный эффект
     * @throws Exception если параметры невалидны
     */
    fun create(params: JsonObject): AbilityEffect
}
