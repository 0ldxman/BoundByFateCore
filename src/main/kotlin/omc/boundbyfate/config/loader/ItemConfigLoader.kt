package omc.boundbyfate.config.loader

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resource.ResourceManager
import net.minecraft.util.Identifier
import omc.boundbyfate.api.item.ItemDefinition
import omc.boundbyfate.registry.ItemPropertyRegistry
import org.slf4j.LoggerFactory

/**
 * Загрузчик [ItemDefinition] из датапаков.
 *
 * Загружает JSON файлы из `data/<namespace>/bbf_item/*.json`
 * и регистрирует их в [ItemPropertyRegistry].
 */
object ItemConfigLoader : SimpleSynchronousResourceReloadListener {

    private val logger = LoggerFactory.getLogger(ItemConfigLoader::class.java)
    private const val DIRECTORY = "bbf_item"

    override fun getFabricId(): Identifier =
        Identifier("boundbyfate-core", "item_loader")

    override fun reload(manager: ResourceManager) {
        logger.info("Loading item definitions from datapacks...")

        ItemPropertyRegistry.clearItemDefinitions()

        val resources = manager.findResources(DIRECTORY) { path ->
            path.path.endsWith(".json")
        }

        var loaded = 0
        var failed = 0

        for ((resourceId, resource) in resources) {
            try {
                resource.reader.use { reader ->
                    val json = JsonParser.parseReader(reader)
                    val result = ItemDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                    result.resultOrPartial { error ->
                        logger.error("Failed to parse item definition '$resourceId': $error")
                        failed++
                    }.ifPresent { definition ->
                        ItemPropertyRegistry.registerItemDefinition(definition)
                        loaded++
                        logger.debug("Loaded item definition: ${definition.item} from ${resourceId.path}")
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to load item definition from '$resourceId'", e)
                failed++
            }
        }

        logger.info("Loaded $loaded item definitions ($failed failed)")
        ItemPropertyRegistry.printStatistics()
    }
}
