package omc.boundbyfate.config.loader

import com.google.gson.JsonParser
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resource.ResourceManager
import net.minecraft.util.Identifier
import com.mojang.serialization.JsonOps
import omc.boundbyfate.api.ability.AbilityDefinition
import omc.boundbyfate.registry.AbilityRegistry
import org.slf4j.LoggerFactory

/**
 * Загрузчик [AbilityDefinition] из датапаков.
 *
 * Загружает JSON файлы из `data/<namespace>/bbf_ability/**/*.json` (рекурсивно)
 * и регистрирует их в [AbilityRegistry].
 *
 * ## Структура файлов
 *
 * Поддерживается произвольная вложенность папок для удобства организации:
 *
 * ```
 * data/
 *   boundbyfate-core/
 *     bbf_ability/
 *       second_wind.json
 *       rage.json
 *       spells/
 *         cantrips/
 *           fire_bolt.json
 *           mage_hand.json
 *         level_1/
 *           magic_missile.json
 *           shield.json
 *         evocation/
 *           fireball.json
 *           lightning_bolt.json
 *       class_features/
 *         fighter/
 *           action_surge.json
 *         barbarian/
 *           rage.json
 * ```
 *
 * ID способности берётся из поля `"id"` в JSON, структура папок не влияет на ID.
 *
 * ## Горячая перезагрузка
 *
 * При `/reload` датапаков все Definition очищаются и загружаются заново.
 * Хендлеры ([omc.boundbyfate.api.ability.AbilityHandler]) не затрагиваются.
 *
 * Примечание: AbilityRegistry использует свою собственную реализацию,
 * поэтому этот загрузчик не использует базовый ConfigLoader.
 */
object AbilityConfigLoader : SimpleSynchronousResourceReloadListener {

    private val logger = LoggerFactory.getLogger(AbilityConfigLoader::class.java)
    private const val DIRECTORY = "bbf_ability"

    override fun getFabricId(): Identifier =
        Identifier("boundbyfate-core", "ability_loader")

    override fun reload(manager: ResourceManager) {
        logger.info("Loading ability definitions from datapacks...")

        // Очищаем старые Definition перед перезагрузкой
        AbilityRegistry.clearDefinitions()

        val resources = manager.findResources(DIRECTORY) { path ->
            path.path.endsWith(".json")
        }

        var loaded = 0
        var failed = 0

        for ((resourceId, resource) in resources) {
            try {
                resource.reader.use { reader ->
                    val json = JsonParser.parseReader(reader)

                    val result = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, json)

                    result.resultOrPartial { error ->
                        logger.error("Failed to parse ability '${resourceId}': $error")
                        failed++
                    }.ifPresent { definition ->
                        AbilityRegistry.registerDefinition(definition)
                        loaded++
                        logger.debug("Loaded ability: ${definition.id} from ${resourceId.path}")
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to load ability from '$resourceId'", e)
                failed++
            }
        }

        logger.info("Loaded $loaded ability definitions ($failed failed)")

        // Предупреждаем о хендлерах без Definition
        val handlersWithoutDef = AbilityRegistry.getAllHandlers()
            .filter { !AbilityRegistry.hasDefinition(it.id) }

        if (handlersWithoutDef.isNotEmpty()) {
            logger.warn(
                "Ability handlers without definitions: " +
                handlersWithoutDef.joinToString { it.id.toString() }
            )
        }
    }
}


