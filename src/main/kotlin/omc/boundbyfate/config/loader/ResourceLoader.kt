package omc.boundbyfate.config.loader

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import net.minecraft.util.Identifier
import omc.boundbyfate.api.core.Registrable
import org.slf4j.LoggerFactory
import java.io.InputStreamReader

/**
 * Загрузчик конфигураций из встроенных ресурсов мода.
 * 
 * Используется для загрузки дефолтных Definition, которые
 * встроены в jar файл мода (не из datapack).
 * 
 * Пример структуры:
 * ```
 * resources/
 *   data/
 *     boundbyfate-core/
 *       bbf_stat/
 *         strength.json
 *         dexterity.json
 * ```
 * 
 * @param T тип загружаемых объектов
 */
class ResourceLoader<T : Registrable>(
    /**
     * ConfigLoader для обработки JSON.
     */
    private val configLoader: ConfigLoader<T>,
    
    /**
     * Базовый путь к ресурсам (например, "/data/boundbyfate-core/bbf_stat").
     */
    private val basePath: String
) {
    private val logger = LoggerFactory.getLogger("ResourceLoader[${configLoader.typeName}]")
    
    /**
     * Загружает один ресурс по пути.
     * 
     * @param resourcePath путь к ресурсу (например, "/data/boundbyfate-core/bbf_stat/strength.json")
     * @param id идентификатор объекта
     * @return true если успешно загружен
     */
    fun loadResource(resourcePath: String, id: Identifier): Boolean {
        return try {
            val inputStream = javaClass.getResourceAsStream(resourcePath)
                ?: run {
                    logger.warn("Resource not found: $resourcePath")
                    return false
                }
            
            InputStreamReader(inputStream).use { reader ->
                configLoader.loadAndRegister(id, reader)
            }
        } catch (e: Exception) {
            logger.error("Failed to load resource $resourcePath", e)
            false
        }
    }
    
    /**
     * Загружает несколько ресурсов.
     * 
     * @param resources Map<имя файла, ID объекта>
     * @return количество успешно загруженных ресурсов
     */
    fun loadResources(resources: Map<String, Identifier>): Int {
        logger.info("Loading ${configLoader.typeName} from resources...")
        
        configLoader.onBeforeLoad()
        
        var loadedCount = 0
        
        for ((fileName, id) in resources) {
            val resourcePath = "$basePath/$fileName"
            
            if (loadResource(resourcePath, id)) {
                loadedCount++
            }
        }
        
        configLoader.onAfterLoad(loadedCount)
        
        return loadedCount
    }
    
    /**
     * Загружает все ресурсы из списка имён файлов.
     * ID извлекается из имени файла.
     * 
     * @param namespace namespace для ID (например, "boundbyfate-core")
     * @param fileNames список имён файлов (например, ["strength.json", "dexterity.json"])
     * @return количество успешно загруженных ресурсов
     */
    fun loadResources(namespace: String, fileNames: List<String>): Int {
        val resources = fileNames.associateWith { fileName ->
            val name = fileName.removeSuffix(".json")
            Identifier.of(namespace, name)
        }
        
        return loadResources(resources)
    }
}
