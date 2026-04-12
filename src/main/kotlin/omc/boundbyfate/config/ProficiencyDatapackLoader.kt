package omc.boundbyfate.config

import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resource.ResourceManager
import net.minecraft.resource.ResourceType
import net.minecraft.util.Identifier
import omc.boundbyfate.api.proficiency.ProficiencyDefinition
import omc.boundbyfate.registry.ProficiencyRegistry
import org.slf4j.LoggerFactory

/**
 * Loads proficiency definitions from datapacks.
 * Reads from data/<namespace>/bbf_proficiency/<name>.json
 */
object ProficiencyDatapackLoader : SimpleSynchronousResourceReloadListener {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")
    private const val PREFIX = "bbf_proficiency"

    override fun getFabricId(): Identifier =
        Identifier("boundbyfate-core", "proficiency_loader")

    override fun reload(manager: ResourceManager) {
        ProficiencyRegistry.clearAll()
        var count = 0

        manager.findResources(PREFIX) { it.path.endsWith(".json") }
            .forEach { (resourceId, resource) ->
                val profId = resourceId.path
                    .removePrefix("$PREFIX/")
                    .removeSuffix(".json")
                    .let { Identifier(resourceId.namespace, it) }

                try {
                    resource.inputStream.use { stream ->
                        val definition: ProficiencyDefinition = ProficiencyParser.parse(profId, stream) ?: return@forEach
                        ProficiencyRegistry.register(definition)
                        count++
                        logger.info("Loaded proficiency: $profId (${definition.displayName})")
                    }
                } catch (e: Exception) {
                    logger.error("Failed to load proficiency from $resourceId", e)
                }
            }

        logger.info("Loaded $count proficiency definitions")
    }

    fun register() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(this)
    }
}
