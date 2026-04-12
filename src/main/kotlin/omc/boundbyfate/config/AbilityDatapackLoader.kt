package omc.boundbyfate.config

import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resource.ResourceManager
import net.minecraft.resource.ResourceType
import net.minecraft.util.Identifier
import omc.boundbyfate.registry.AbilityRegistry
import org.slf4j.LoggerFactory

/**
 * Загружает определения способностей из датапаков.
 * Читает из data/<namespace>/bbf_ability/<name>.json
 */
object AbilityDatapackLoader : SimpleSynchronousResourceReloadListener {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")
    private const val PREFIX = "bbf_ability"
    
    override fun getFabricId(): Identifier =
        Identifier("boundbyfate-core", "ability_loader")
    
    override fun reload(manager: ResourceManager) {
        AbilityRegistry.clear()
        var count = 0
        
        // Загружаем в два прохода для поддержки оверрайдов
        val resources = manager.findResources(PREFIX) { it.path.endsWith(".json") }
        
        // Первый проход: загружаем базовые способности (без "base")
        resources.forEach { (resourceId, resource) ->
            val abilityId = resourceId.path
                .removePrefix("$PREFIX/")
                .removeSuffix(".json")
                .let { Identifier(resourceId.namespace, it) }
            
            try {
                resource.inputStream.use { stream ->
                    // Проверяем, есть ли "base" в JSON
                    val content = stream.readBytes().toString(Charsets.UTF_8)
                    if (!content.contains("\"base\"")) {
                        val def = AbilityParser.parse(abilityId, content.byteInputStream()) ?: return@forEach
                        AbilityRegistry.register(def)
                        count++
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to load ability from $resourceId", e)
            }
        }
        
        // Второй проход: загружаем оверрайды (с "base")
        resources.forEach { (resourceId, resource) ->
            val abilityId = resourceId.path
                .removePrefix("$PREFIX/")
                .removeSuffix(".json")
                .let { Identifier(resourceId.namespace, it) }
            
            try {
                resource.inputStream.use { stream ->
                    val content = stream.readBytes().toString(Charsets.UTF_8)
                    if (content.contains("\"base\"")) {
                        val def = AbilityParser.parse(abilityId, content.byteInputStream()) ?: return@forEach
                        AbilityRegistry.register(def)
                        count++
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to load ability override from $resourceId", e)
            }
        }
        
        logger.info("Loaded $count ability definitions")
    }
    
    fun register() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
            .registerReloadListener(this)
    }
}
