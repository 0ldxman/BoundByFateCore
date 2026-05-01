package omc.boundbyfate.config.loader

import com.google.gson.JsonParser
import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resource.ResourceManager
import net.minecraft.util.Identifier
import omc.boundbyfate.api.core.Registrable
import omc.boundbyfate.registry.core.BbfDualRegistry
import org.slf4j.LoggerFactory

/**
 * Загрузчик дефиниций для [BbfDualRegistry].
 *
 * Загружает JSON файлы из `data/<namespace>/<directory>/` и регистрирует
 * их как дефиниции в указанном dual-реестре.
 *
 * Поддерживает произвольную вложенность папок — ID берётся из поля `"id"` в JSON,
 * а не из пути к файлу. Это позволяет организовывать файлы в подпапки:
 *
 * ```
 * data/boundbyfate-core/bbf_ability/
 *   second_wind.json
 *   spells/
 *     cantrips/
 *       fire_bolt.json
 *     level_1/
 *       magic_missile.json
 * ```
 *
 * После `/reload` дефиниции очищаются и загружаются заново.
 * Хендлеры не затрагиваются.
 *
 * ## Использование
 *
 * ```kotlin
 * ConfigManager.registerDatapackLoader(
 *     DualDatapackLoader(AbilityRegistry, AbilityDefinition.CODEC, "bbf_ability")
 * )
 * ```
 *
 * @param registry реестр в который загружаются дефиниции
 * @param codec Codec для десериализации JSON → D
 * @param directory директория в датапаке (например "bbf_ability", "bbf_item")
 * @param typeName имя типа для логирования (по умолчанию — имя реестра)
 */
class DualDatapackLoader<H : Any, D : Registrable>(
    private val registry: BbfDualRegistry<H, D>,
    private val codec: Codec<D>,
    private val directory: String,
    private val typeName: String = registry.name,
    /**
     * Опциональный хук после загрузки.
     * Вызывается после регистрации всех дефиниций.
     * Используй для валидации (например, проверки хендлеров без дефиниций).
     */
    private val onAfterLoad: ((loaded: Int, failed: Int) -> Unit)? = null
) : SimpleSynchronousResourceReloadListener {

    private val logger = LoggerFactory.getLogger("DualDatapackLoader[$typeName]")

    override fun getFabricId(): Identifier =
        Identifier("boundbyfate-core", "${typeName}_loader")

    override fun reload(manager: ResourceManager) {
        logger.info("Loading $typeName definitions from datapacks...")

        registry.clearDefinitions()

        val resources = manager.findResources(directory) { path ->
            path.path.endsWith(".json")
        }

        var loaded = 0
        var failed = 0

        for ((resourceId, resource) in resources) {
            try {
                resource.reader.use { reader ->
                    val json = JsonParser.parseReader(reader)
                    val result = codec.parse(JsonOps.INSTANCE, json)

                    result.resultOrPartial { error ->
                        logger.error("Failed to parse $typeName from '$resourceId': $error")
                        failed++
                    }.ifPresent { definition ->
                        registry.registerDefinition(definition)
                        loaded++
                        logger.debug("Loaded $typeName: ${definition.id}")
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to load $typeName from '$resourceId'", e)
                failed++
            }
        }

        logger.info("Loaded $loaded $typeName definitions ($failed failed)")

        onAfterLoad?.invoke(loaded, failed)
    }
}
