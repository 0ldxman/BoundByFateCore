package omc.boundbyfate.config

import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.minecraft.resource.ResourceType
import omc.boundbyfate.api.core.Registrable
import omc.boundbyfate.config.loader.ConfigLoader
import omc.boundbyfate.config.loader.DatapackLoader
import omc.boundbyfate.config.loader.ResourceLoader
import org.slf4j.LoggerFactory

/**
 * Центральный менеджер для управления загрузкой конфигураций.
 * 
 * Отвечает за:
 * - Регистрацию ConfigLoader'ов
 * - Интеграцию с Minecraft Resource System
 * - Координацию загрузки из resources и datapack
 */
object ConfigManager {
    private val logger = LoggerFactory.getLogger(ConfigManager::class.java)
    
    /**
     * Зарегистрированные загрузчики.
     */
    private val loaders: MutableList<DatapackLoader<*>> = mutableListOf()
    
    /**
     * Регистрирует ConfigLoader для загрузки из datapack.
     * 
     * @param configLoader загрузчик конфигураций
     * @param directory директория в datapack (например, "bbf_stat")
     * @return DatapackLoader для дополнительной настройки
     */
    fun <T : Registrable> registerDatapackLoader(
        configLoader: ConfigLoader<T>,
        directory: String
    ): DatapackLoader<T> {
        val datapackLoader = DatapackLoader(configLoader, directory)
        
        // Регистрируем в Fabric Resource Manager
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(datapackLoader)
        
        loaders.add(datapackLoader)
        
        logger.info("Registered datapack loader for ${configLoader.typeName} (directory: $directory)")
        
        return datapackLoader
    }
    
    /**
     * Создаёт ResourceLoader для загрузки встроенных ресурсов.
     * 
     * @param configLoader загрузчик конфигураций
     * @param basePath базовый путь к ресурсам
     * @return ResourceLoader для загрузки
     */
    fun <T : Registrable> createResourceLoader(
        configLoader: ConfigLoader<T>,
        basePath: String
    ): ResourceLoader<T> {
        return ResourceLoader(configLoader, basePath)
    }
    
    /**
     * Выводит статистику по зарегистрированным загрузчикам.
     */
    fun printStatistics() {
        logger.info("=== Config Loader Statistics ===")
        logger.info("Registered loaders: ${loaders.size}")
        logger.info("================================")
    }
}
