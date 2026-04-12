package omc.boundbyfate.config

import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resource.ResourceManager
import net.minecraft.resource.ResourceType
import net.minecraft.util.Identifier
import omc.boundbyfate.api.feature.BbfStatusEffectDefinition
import omc.boundbyfate.api.feature.FeatureDefinition
import omc.boundbyfate.registry.FeatureRegistry
import org.slf4j.LoggerFactory

/**
 * Loads feature and status effect definitions from datapacks.
 * - data/<namespace>/bbf_feature/<name>.json
 * - data/<namespace>/bbf_status/<name>.json
 */
object FeatureDatapackLoader : SimpleSynchronousResourceReloadListener {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    override fun getFabricId(): Identifier =
        Identifier("boundbyfate-core", "feature_loader")

    override fun reload(manager: ResourceManager) {
        FeatureRegistry.clearAll()
        var featureCount = 0
        var statusCount = 0

        // Load features
        manager.findResources("bbf_feature") { it.path.endsWith(".json") }
            .forEach { (resourceId, resource) ->
                val featId = Identifier(
                    resourceId.namespace,
                    resourceId.path.removePrefix("bbf_feature/").removeSuffix(".json")
                )
                try {
                    resource.inputStream.use { stream ->
                        val def: FeatureDefinition = FeatureParser.parseFeature(featId, stream) ?: return@forEach
                        FeatureRegistry.registerFeature(def)
                        featureCount++
                    }
                } catch (e: Exception) {
                    logger.error("Failed to load feature $featId", e)
                }
            }

        // Load status effects
        manager.findResources("bbf_status") { it.path.endsWith(".json") }
            .forEach { (resourceId, resource) ->
                val statusId = Identifier(
                    resourceId.namespace,
                    resourceId.path.removePrefix("bbf_status/").removeSuffix(".json")
                )
                try {
                    resource.inputStream.use { stream ->
                        val def: BbfStatusEffectDefinition = FeatureParser.parseStatus(statusId, stream) ?: return@forEach
                        FeatureRegistry.registerStatus(def)
                        statusCount++
                    }
                } catch (e: Exception) {
                    logger.error("Failed to load status $statusId", e)
                }
            }

        logger.info("Loaded $featureCount features and $statusCount status effects")
    }

    fun register() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(this)
    }
}
