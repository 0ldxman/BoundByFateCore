package omc.boundbyfate.config

import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resource.ResourceManager
import net.minecraft.resource.ResourceType
import net.minecraft.util.Identifier
import omc.boundbyfate.api.feat.FeatDefinition
import omc.boundbyfate.registry.FeatRegistry
import org.slf4j.LoggerFactory

/**
 * Loads feat definitions from datapacks.
 * Reads from data/<namespace>/bbf_feat/<name>.json
 */
object FeatDatapackLoader : SimpleSynchronousResourceReloadListener {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")
    private const val PREFIX = "bbf_feat"

    override fun getFabricId(): Identifier =
        Identifier("boundbyfate-core", "feat_loader")

    override fun reload(manager: ResourceManager) {
        FeatRegistry.clearAll()
        var count = 0

        manager.findResources(PREFIX) { it.path.endsWith(".json") }
            .forEach { (resourceId, resource) ->
                val featId = Identifier(
                    resourceId.namespace,
                    resourceId.path.removePrefix("$PREFIX/").removeSuffix(".json")
                )

                try {
                    resource.inputStream.use { stream ->
                        val definition: FeatDefinition = FeatParser.parse(featId, stream) ?: return@forEach
                        FeatRegistry.register(definition)
                        count++
                        logger.info("Loaded feat: $featId (${definition.displayName})")
                    }
                } catch (e: Exception) {
                    logger.error("Failed to load feat from $resourceId", e)
                }
            }

        logger.info("Loaded $count feat definitions")
    }

    fun register() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(this)
    }
}
