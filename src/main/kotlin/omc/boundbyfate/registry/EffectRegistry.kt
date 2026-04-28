package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.effect.EffectDefinition
import omc.boundbyfate.api.effect.EffectHandler
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Реестр эффектов.
 *
 * Аналог [AbilityRegistry] — хранит два независимых хранилища:
 * - [EffectHandler] — логика, регистрируется в коде
 * - [EffectDefinition] — данные, загружаются из JSON датапаков
 *
 * Они связываются по одному [Identifier].
 *
 * ## Регистрация хендлера
 *
 * ```kotlin
 * // В BbfEffects.register():
 * EffectRegistry.register(Darkvision)
 * EffectRegistry.register(StatModifier)
 * EffectRegistry.register(Poison)
 * ```
 *
 * ## Загрузка Definition
 *
 * Definition загружаются автоматически из датапаков.
 * Путь: `data/<namespace>/bbf_effect/<name>.json`
 *
 * ## Получение
 *
 * ```kotlin
 * val handler = EffectRegistry.getHandler(id)
 * val definition = EffectRegistry.getDefinition(id)
 * val pair = EffectRegistry.get(id) // Pair<handler, definition> или null
 * ```
 *
 * ## Применение эффекта
 *
 * ```kotlin
 * val (handler, definition) = EffectRegistry.get(effectId) ?: return
 * val ctx = EffectContext.passive(entity, definition, source)
 * handler.apply(ctx)
 * ```
 */
object EffectRegistry {

    private val logger = LoggerFactory.getLogger(EffectRegistry::class.java)

    private val handlers: ConcurrentHashMap<Identifier, EffectHandler> = ConcurrentHashMap()
    private val definitions: ConcurrentHashMap<Identifier, EffectDefinition> = ConcurrentHashMap()

    // ── Регистрация хендлеров ─────────────────────────────────────────────

    /**
     * Регистрирует хендлер эффекта.
     *
     * @throws IllegalStateException если хендлер с таким ID уже зарегистрирован
     */
    fun register(handler: EffectHandler) {
        if (handlers.containsKey(handler.id)) {
            throw IllegalStateException(
                "EffectHandler '${handler.id}' is already registered"
            )
        }
        handlers[handler.id] = handler
        logger.debug("Registered effect handler: ${handler.id}")
    }

    /**
     * Регистрирует хендлер, перезаписывая существующий.
     * Используй для переопределения встроенных эффектов.
     */
    fun registerOrReplace(handler: EffectHandler) {
        if (handlers.containsKey(handler.id)) {
            logger.warn("Replacing effect handler: ${handler.id}")
        }
        handlers[handler.id] = handler
    }

    // ── Регистрация Definition ────────────────────────────────────────────

    /**
     * Регистрирует Definition эффекта.
     * Вызывается загрузчиком датапаков.
     */
    fun registerDefinition(definition: EffectDefinition) {
        definitions[definition.id] = definition
        logger.debug("Registered effect definition: ${definition.id}")
    }

    /**
     * Очищает все Definition (вызывается при перезагрузке датапаков).
     */
    fun clearDefinitions() {
        definitions.clear()
        logger.info("Cleared all effect definitions")
    }

    // ── Получение ─────────────────────────────────────────────────────────

    /**
     * Возвращает хендлер по ID или null.
     */
    fun getHandler(id: Identifier): EffectHandler? = handlers[id]

    /**
     * Возвращает Definition по ID или null.
     */
    fun getDefinition(id: Identifier): EffectDefinition? = definitions[id]

    /**
     * Возвращает пару (хендлер, definition) или null если что-то не найдено.
     */
    fun get(id: Identifier): Pair<EffectHandler, EffectDefinition>? {
        val handler = handlers[id] ?: return null
        val definition = definitions[id] ?: return null
        return handler to definition
    }

    /**
     * Возвращает все зарегистрированные хендлеры.
     */
    fun getAllHandlers(): Collection<EffectHandler> = handlers.values

    /**
     * Возвращает все загруженные Definition.
     */
    fun getAllDefinitions(): Collection<EffectDefinition> = definitions.values

    /**
     * Возвращает все тикующие хендлеры (tickInterval > 0).
     */
    fun getTickingHandlers(): Collection<EffectHandler> =
        handlers.values.filter { it.isTicking }

    fun hasHandler(id: Identifier): Boolean = handlers.containsKey(id)
    fun hasDefinition(id: Identifier): Boolean = definitions.containsKey(id)
    fun handlerCount(): Int = handlers.size
    fun definitionCount(): Int = definitions.size

    fun printStatistics() {
        logger.info("=== Effect Registry ===")
        logger.info("  Handlers: ${handlerCount()}")
        logger.info("  Definitions: ${definitionCount()}")
        logger.info("  Ticking: ${getTickingHandlers().size}")
        val missing = handlers.keys.filter { !definitions.containsKey(it) }
        if (missing.isNotEmpty()) {
            logger.warn("  Handlers without definitions: $missing")
        }
        logger.info("=======================")
    }
}
