package omc.boundbyfate.config.loader

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resource.ResourceManager
import net.minecraft.util.Identifier
import omc.boundbyfate.api.alignment.AlignmentConfig
import omc.boundbyfate.system.alignment.AlignmentSystem
import org.slf4j.LoggerFactory

/**
 * Загрузчик конфигурации мировоззрений.
 *
 * Загружает единственный файл:
 * `data/<namespace>/alignment_config.json`
 *
 * Приоритет: если файл найден в нескольких датапаках,
 * используется последний (стандартное поведение Minecraft).
 *
 * ## Структура файла
 *
 * ```json
 * {
 *   "grid_size": 6,
 *   "alignments": [
 *     {"id": "boundbyfate-core:true_neutral",    "x_range": [-2,  2], "y_range": [-2,  2]},
 *     {"id": "boundbyfate-core:lawful_good",     "x_range": [-6, -3], "y_range": [ 3,  6]},
 *     ...
 *   ]
 * }
 * ```
 */
object AlignmentConfigLoader : SimpleSynchronousResourceReloadListener {

    private val logger = LoggerFactory.getLogger(AlignmentConfigLoader::class.java)
    private val RESOURCE_ID = Identifier.of("boundbyfate-core", "alignment_config_loader")
    private const val FILE_PATH = "alignment_config.json"

    override fun getFabricId(): Identifier = RESOURCE_ID

    override fun reload(manager: ResourceManager) {
        logger.info("Loading alignment config...")

        // Ищем файл во всех датапаках (последний выигрывает)
        val resources = manager.findResources(FILE_PATH) { it.path.endsWith(FILE_PATH) }

        if (resources.isEmpty()) {
            logger.warn("No alignment_config.json found — using default config")
            return
        }

        // Берём последний найденный файл (наивысший приоритет датапака)
        val (resourceId, resource) = resources.entries.last()

        try {
            resource.reader.use { reader ->
                val json = JsonParser.parseReader(reader)
                val result = AlignmentConfig.CODEC.parse(JsonOps.INSTANCE, json)

                result.resultOrPartial { error ->
                    logger.error("Failed to parse alignment config from $resourceId: $error")
                }.ifPresent { config ->
                    AlignmentSystem.loadConfig(config)
                    logger.info(
                        "Loaded alignment config from $resourceId: " +
                        "grid_size=${config.gridSize}, alignments=${config.alignments.size}"
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to load alignment config from $resourceId", e)
        }
    }
}
