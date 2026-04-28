package omc.boundbyfate.config.loader

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resource.ResourceManager
import net.minecraft.util.Identifier
import omc.boundbyfate.api.mechanic.MechanicDefinition
import omc.boundbyfate.system.mechanic.MechanicRegistry
import org.slf4j.LoggerFactory

/**
 * Загрузчик [MechanicDefinition] из датапаков.
 *
 * Загружает JSON файлы из `data/<namespace>/bbf_mechanic/**/*.json` (рекурсивно)
 * и регистрирует их в [MechanicRegistry].
 *
 * ## Структура файлов
 *
 * Поддерживается произвольная вложенность папок для удобства организации:
 *
 * ```
 * data/
 *   boundbyfate-core/
 *     bbf_mechanic/
 *       spellcasting.json
 *       rage.json
 *       spellcasting/
 *         wizard_spellbook.json
 *         metamagic.json
 *         pact_magic.json
 *       combat/
 *         rage.json
 *         sneak_attack.json
 *       resources/
 *         ki.json
 *         superiority_dice.json
 * ```
 *
 * ID механики берётся из поля `"id"` в JSON, структура папок не влияет на ID.
 *
 * ## Горячая перезагрузка
 *
 * При `/reload` датапаков все Definition очищаются и загружаются заново.
 * Хендлеры ([omc.boundbyfate.api.mechanic.ClassMechanic]) не затрагиваются.
 *
 * Примечание: MechanicRegistry использует свою собственную реализацию,
 * поэтому этот загрузчик не использует базовый ConfigLoader.
 */
object MechanicConfigLoader : SimpleSynchronousResourceReloadListener {
    
    private val logger = LoggerFactory.getLogger(MechanicConfigLoader::class.java)
    private const val DIRECTORY = "bbf_mechanic"
    
    override fun getFabricId(): Identifier =
        Identifier("boundbyfate-core", "mechanic_loader")
    
    override fun reload(manager: ResourceManager) {
        logger.info("Loading mechanic definitions from datapacks...")
        
        // Очищаем старые Definition перед перезагрузкой
        MechanicRegistry.clearDefinitions()
        
        val resources = manager.findResources(DIRECTORY) { path ->
            path.path.endsWith(".json")
        }
        
        var loaded = 0
        var failed = 0
        
        for ((resourceId, resource) in resources) {
            try {
                resource.reader.use { reader ->
                    val json = JsonParser.parseReader(reader)
                    
                    val result = MechanicDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                    
                    result.resultOrPartial { error ->
                        logger.error("Failed to parse mechanic '${resourceId}': $error")
                        failed++
                    }.ifPresent { definition ->
                        MechanicRegistry.registerDefinition(definition)
                        loaded++
                        logger.debug("Loaded mechanic: ${definition.id} from ${resourceId.path}")
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to load mechanic from '$resourceId'", e)
                failed++
            }
        }
        
        logger.info("Loaded $loaded mechanic definitions ($failed failed)")
        
        // Предупреждаем о хендлерах без Definition
        val handlersWithoutDef = MechanicRegistry.getAllHandlers()
            .filter { !MechanicRegistry.hasDefinition(it.id) }
        
        if (handlersWithoutDef.isNotEmpty()) {
            logger.warn(
                "Mechanic handlers without definitions: " +
                handlersWithoutDef.joinToString { it.id.toString() }
            )
        }
    }
}

