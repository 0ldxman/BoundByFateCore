package omc.boundbyfate.config

import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resource.ResourceManager
import net.minecraft.resource.ResourceType
import net.minecraft.util.Identifier
import omc.boundbyfate.api.race.RaceDefinition
import omc.boundbyfate.api.race.SubraceDefinition
import omc.boundbyfate.registry.RaceRegistry
import org.slf4j.LoggerFactory

/**
 * Loads race and subrace definitions from datapacks.
 * Reads from:
 * - data/<namespace>/bbf_race/<name>.json
 * - data/<namespace>/bbf_subrace/<name>.json
 */
object RaceDatapackLoader : SimpleSynchronousResourceReloadListener {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    override fun getFabricId(): Identifier =
        Identifier("boundbyfate-core", "race_loader")

    override fun reload(manager: ResourceManager) {
        RaceRegistry.clearAll()
        var raceCount = 0
        var subraceCount = 0

        manager.findResources("bbf_race") { it.path.endsWith(".json") }
            .forEach { (resourceId, resource) ->
                val raceId = Identifier(
                    resourceId.namespace,
                    resourceId.path.removePrefix("bbf_race/").removeSuffix(".json")
                )
                try {
                    resource.inputStream.use { stream ->
                        val def: RaceDefinition = RaceParser.parseRace(raceId, stream) ?: return@forEach
                        RaceRegistry.registerRace(def)
                        raceCount++
                        logger.info("Loaded race: $raceId (${def.displayName})")
                    }
                } catch (e: Exception) {
                    logger.error("Failed to load race from $resourceId", e)
                }
            }

        manager.findResources("bbf_subrace") { it.path.endsWith(".json") }
            .forEach { (resourceId, resource) ->
                val subraceId = Identifier(
                    resourceId.namespace,
                    resourceId.path.removePrefix("bbf_subrace/").removeSuffix(".json")
                )
                try {
                    resource.inputStream.use { stream ->
                        val def: SubraceDefinition = RaceParser.parseSubrace(subraceId, stream) ?: return@forEach
                        RaceRegistry.registerSubrace(def)
                        subraceCount++
                        logger.info("Loaded subrace: $subraceId (${def.displayName})")
                    }
                } catch (e: Exception) {
                    logger.error("Failed to load subrace from $resourceId", e)
                }
            }

        logger.info("Loaded $raceCount races and $subraceCount subraces")
    }

    fun register() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(this)
    }
}
