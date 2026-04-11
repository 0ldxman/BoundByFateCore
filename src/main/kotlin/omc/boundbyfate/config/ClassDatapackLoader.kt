package omc.boundbyfate.config

import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resource.ResourceManager
import net.minecraft.resource.ResourceType
import net.minecraft.util.Identifier
import omc.boundbyfate.registry.ClassRegistry
import omc.boundbyfate.config.BbfClassParser
import omc.boundbyfate.api.charclass.ClassDefinition
import omc.boundbyfate.api.charclass.SubclassDefinition
import org.slf4j.LoggerFactory

/**
 * Loads class and subclass definitions from datapacks on server start/reload.
 *
 * Listens for files in:
 * - data/<namespace>/bbf_class/<name>.json
 * - data/<namespace>/bbf_subclass/<name>.json
 */
object ClassDatapackLoader : SimpleSynchronousResourceReloadListener {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    private val CLASS_PREFIX = "bbf_class"
    private val SUBCLASS_PREFIX = "bbf_subclass"

    override fun getFabricId(): Identifier =
        Identifier("boundbyfate-core", "class_loader")

    override fun reload(manager: ResourceManager) {
        ClassRegistry.clearAll()

        var classCount = 0
        var subclassCount = 0

        // Load classes
        manager.findResources(CLASS_PREFIX) { it.path.endsWith(".json") }
            .forEach { (resourceId, resource) ->
                val classId = resourceIdToDefinitionId(resourceId, CLASS_PREFIX) ?: return@forEach

                try {
                    resource.inputStream.use { stream ->
                        val definition: ClassDefinition = BbfClassParser.loadClass(classId, stream) ?: return@forEach
                        ClassRegistry.registerClass(definition)
                        classCount++
                        logger.info("Loaded class: $classId (${definition.displayName})")
                    }
                } catch (e: Exception) {
                    logger.error("Failed to load class from $resourceId", e)
                }
            }

        // Load subclasses
        manager.findResources(SUBCLASS_PREFIX) { it.path.endsWith(".json") }
            .forEach { (resourceId, resource) ->
                val subclassId = resourceIdToDefinitionId(resourceId, SUBCLASS_PREFIX) ?: return@forEach

                try {
                    resource.inputStream.use { stream ->
                        val definition: SubclassDefinition = BbfClassParser.loadSubclass(subclassId, stream) ?: return@forEach
                        ClassRegistry.registerSubclass(definition)
                        subclassCount++
                        logger.info("Loaded subclass: $subclassId (${definition.displayName})")
                    }
                } catch (e: Exception) {
                    logger.error("Failed to load subclass from $resourceId", e)
                }
            }

        logger.info("Loaded $classCount classes and $subclassCount subclasses")
    }

    /**
     * Converts a resource path like "boundbyfate-core:bbf_class/fighter.json"
     * to an identifier like "boundbyfate-core:fighter"
     */
    private fun resourceIdToDefinitionId(resourceId: Identifier, prefix: String): Identifier? {
        val path = resourceId.path
            .removePrefix("$prefix/")
            .removeSuffix(".json")

        if (path.isBlank()) return null

        return Identifier(resourceId.namespace, path)
    }

    /**
     * Registers this loader with Fabric's resource system.
     * Call during mod initialization.
     */
    fun register() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(this)
    }
}
