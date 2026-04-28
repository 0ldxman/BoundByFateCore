package omc.boundbyfate.config.loader

import com.google.gson.JsonElement
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resource.ResourceManager
import net.minecraft.util.Identifier
import omc.boundbyfate.api.core.Registrable
import org.slf4j.LoggerFactory

/**
 * Загрузчик конфигураций из datapack.
 * 
 * Интегрируется с Minecraft Resource System для загрузки JSON файлов
 * из `data/<namespace>/<directory>/` с поддержкой hot reload.
 * 
 * Пример структуры:
 * ```
 * data/
 *   boundbyfate-core/
 *     bbf_stat/
 *       strength.json
 *       dexterity.json
 *     bbf_class/
 *       fighter.json
 * ```
 * 
 * @param T тип загружаемых объектов
 */
class DatapackLoader<T : Registrable>(
    /**
     * ConfigLoader для обработки JSON.
     */
    private val configLoader: ConfigLoader<T>,
    
    /**
     * Директория в datapack (например, "bbf_stat", "bbf_class").
     */
    private val directory: String
) : SimpleSynchronousResourceReloadListener {
    
    private val logger = LoggerFactory.getLogger("DatapackLoader[${configLoader.typeName}]")
    
    /**
     * ID для регистрации в Resource Manager.
     */
    private val listenerId = Identifier.of("boundbyfate-core", "${configLoader.typeName}_loader")
    
    override fun getFabricId(): Identifier = listenerId
    
    /**
     * Вызывается при загрузке/перезагрузке ресурсов.
     */
    override fun reload(manager: ResourceManager) {
        logger.info("Loading ${configLoader.typeName} from datapack...")
        
        configLoader.onBeforeLoad()
        
        // Очищаем registry перед перезагрузкой
        configLoader.registry.clear()
        
        // Загружаем все JSON файлы из директории
        val resources = manager.findResources(directory) { path ->
            path.path.endsWith(".json")
        }
        
        var loadedCount = 0
        
        for ((identifier, resource) in resources) {
            try {
                resource.reader.use { reader ->
                    // Извлекаем ID из пути файла
                    // Например: "data/boundbyfate-core/bbf_stat/strength.json" -> "boundbyfate-core:strength"
                    val id = extractId(identifier)
                    
                    if (configLoader.loadAndRegister(id, reader)) {
                        loadedCount++
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to load resource $identifier", e)
            }
        }
        
        configLoader.onAfterLoad(loadedCount)
        
        logger.info("Loaded $loadedCount ${configLoader.typeName} definitions from datapack")
    }
    
    /**
     * Извлекает ID объекта из пути к ресурсу.
     * 
     * Структура папок игнорируется - берётся только имя файла.
     * Это позволяет организовывать файлы в произвольные подпапки.
     * 
     * Примеры:
     * - Input: "boundbyfate-core:bbf_stat/strength.json"
     *   Output: "boundbyfate-core:strength"
     * 
     * - Input: "boundbyfate-core:bbf_ability/spells/evocation/fireball.json"
     *   Output: "boundbyfate-core:fireball"
     * 
     * - Input: "boundbyfate-core:bbf_race/humanoid/elf/high_elf.json"
     *   Output: "boundbyfate-core:high_elf"
     */
    private fun extractId(resourceId: Identifier): Identifier {
        val path = resourceId.path
        
        // Берём только имя файла (последний сегмент пути) без расширения
        val fileName = path
            .substringAfterLast('/')  // Берём всё после последнего /
            .removeSuffix(".json")
        
        return Identifier.of(resourceId.namespace, fileName)
    }
}
