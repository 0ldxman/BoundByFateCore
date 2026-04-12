package omc.boundbyfate.config

import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resource.ResourceManager
import net.minecraft.resource.ResourceType
import net.minecraft.util.Identifier
import omc.boundbyfate.registry.WeaponRegistry
import org.slf4j.LoggerFactory

/**
 * Loads weapon definitions from datapacks.
 * Reads from data/<namespace>/bbf_weapon/<name>.json
 */
object WeaponDatapackLoader : SimpleSynchronousResourceReloadListener {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")
    private const val PREFIX = "bbf_weapon"

    override fun getFabricId(): Identifier =
        Identifier("boundbyfate-core", "weapon_loader")

    override fun reload(manager: ResourceManager) {
        WeaponRegistry.clearAll()
        var count = 0

        manager.findResources(PREFIX) { it.path.endsWith(".json") }
            .forEach { (resourceId, resource) ->
                val weaponId = resourceId.path
                    .removePrefix("$PREFIX/")
                    .removeSuffix(".json")
                    .let { Identifier(resourceId.namespace, it) }

                try {
                    resource.inputStream.use { stream ->
                        val def = WeaponParser.parse(weaponId, stream) ?: return@forEach
                        WeaponRegistry.register(def)
                        count++
                    }
                } catch (e: Exception) {
                    logger.error("Failed to load weapon from $resourceId", e)
                }
            }

        logger.info("Loaded $count weapon definitions")
    }

    fun register() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(this)
    }
}
