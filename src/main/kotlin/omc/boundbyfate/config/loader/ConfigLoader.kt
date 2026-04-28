package omc.boundbyfate.config.loader

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import net.minecraft.util.Identifier
import omc.boundbyfate.api.core.Registrable
import omc.boundbyfate.registry.core.BbfRegistry
import org.slf4j.LoggerFactory
import java.io.Reader

/**
 * Базовый загрузчик конфигураций из JSON.
 * 
 * Предоставляет общую логику для загрузки Definition из JSON файлов
 * с использованием Minecraft Codec для типобезопасной десериализации.
 * 
 * @param T тип загружаемых объектов (должен быть Registrable)
 */
abstract class ConfigLoader<T : Registrable>(
    /**
     * Имя типа конфигурации (для логирования).
     * Например: "stat", "class", "race"
     */
    val typeName: String,
    
    /**
     * Codec для десериализации JSON в объект типа T.
     */
    val codec: Codec<T>,
    
    /**
     * Registry для регистрации загруженных объектов.
     */
    val registry: BbfRegistry<T>
) {
    protected val logger = LoggerFactory.getLogger("ConfigLoader[$typeName]")
    
    /**
     * Загружает один объект из JSON.
     * 
     * @param id идентификатор объекта
     * @param reader Reader для чтения JSON
     * @return загруженный объект или null при ошибке
     */
    fun loadSingle(id: Identifier, reader: Reader): T? {
        return try {
            // Парсим JSON
            val jsonElement = JsonParser.parseReader(reader)
            
            // Десериализуем через Codec
            val result = codec.parse(JsonOps.INSTANCE, jsonElement)
            
            // Обрабатываем результат
            result.resultOrPartial { error ->
                logger.error("Failed to parse $typeName $id: $error")
            }.orElse(null)
            
        } catch (e: Exception) {
            logger.error("Failed to load $typeName $id", e)
            null
        }
    }
    
    /**
     * Загружает один объект из JsonElement.
     * 
     * @param id идентификатор объекта
     * @param json JSON элемент
     * @return загруженный объект или null при ошибке
     */
    fun loadSingle(id: Identifier, json: JsonElement): T? {
        return try {
            val result = codec.parse(JsonOps.INSTANCE, json)
            
            result.resultOrPartial { error ->
                logger.error("Failed to parse $typeName $id: $error")
            }.orElse(null)
            
        } catch (e: Exception) {
            logger.error("Failed to load $typeName $id", e)
            null
        }
    }
    
    /**
     * Загружает и регистрирует один объект.
     * 
     * @param id идентификатор объекта
     * @param reader Reader для чтения JSON
     * @return true если успешно загружен и зарегистрирован
     */
    fun loadAndRegister(id: Identifier, reader: Reader): Boolean {
        val obj = loadSingle(id, reader) ?: return false
        
        return try {
            registry.register(obj)
            logger.debug("Loaded and registered $typeName: $id")
            true
        } catch (e: Exception) {
            logger.error("Failed to register $typeName $id", e)
            false
        }
    }
    
    /**
     * Загружает и регистрирует один объект из JsonElement.
     * 
     * @param id идентификатор объекта
     * @param json JSON элемент
     * @return true если успешно загружен и зарегистрирован
     */
    fun loadAndRegister(id: Identifier, json: JsonElement): Boolean {
        val obj = loadSingle(id, json) ?: return false
        
        return try {
            registry.register(obj)
            logger.debug("Loaded and registered $typeName: $id")
            true
        } catch (e: Exception) {
            logger.error("Failed to register $typeName $id", e)
            false
        }
    }
    
    /**
     * Загружает несколько объектов из Map.
     * 
     * @param data Map<Identifier, JsonElement>
     * @return количество успешно загруженных объектов
     */
    fun loadMultiple(data: Map<Identifier, JsonElement>): Int {
        var loaded = 0
        
        for ((id, json) in data) {
            if (loadAndRegister(id, json)) {
                loaded++
            }
        }
        
        logger.info("Loaded $loaded/${ data.size} $typeName definitions")
        return loaded
    }
    
    /**
     * Callback вызываемый перед загрузкой.
     * Можно переопределить для пользовательской логики.
     */
    open fun onBeforeLoad() {
        logger.debug("Starting to load $typeName definitions")
    }
    
    /**
     * Callback вызываемый после загрузки.
     * Можно переопределить для пост-обработки.
     */
    open fun onAfterLoad(loadedCount: Int) {
        logger.info("Finished loading $typeName: $loadedCount definitions")
    }
}
