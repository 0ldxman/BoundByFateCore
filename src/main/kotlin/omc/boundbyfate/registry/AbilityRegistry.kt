package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.ability.AbilityDefinition
import omc.boundbyfate.api.ability.AbilityHandler
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Реестр способностей.
 *
 * Хранит два независимых хранилища:
 * - [AbilityHandler] — логика, регистрируется в коде
 * - [AbilityDefinition] — данные, загружаются из JSON датапаков
 *
 * Они связываются по одному [Identifier].
 *
 * ## Регистрация хендлера
 *
 * ```kotlin
 * // В BbfAbilities.register():
 * AbilityRegistry.register(SecondWind)
 * AbilityRegistry.register(Fireball)
 * ```
 *
 * ## Загрузка Definition
 *
 * Definition загружаются автоматически из датапаков через загрузчик.
 * Вручную можно через [registerDefinition].
 *
 * ## Получение
 *
 * ```kotlin
 * val handler = AbilityRegistry.getHandler(id)
 * val definition = AbilityRegistry.getDefinition(id)
 * val pair = AbilityRegistry.get(id) // Pair<handler, definition> или null
 * ```
 */
object AbilityRegistry {

    private val logger = LoggerFactory.getLogger(AbilityRegistry::class.java)

    private val handlers: ConcurrentHashMap<Identifier, AbilityHandler> = ConcurrentHashMap()
    private val definitions: ConcurrentHashMap<Identifier, AbilityDefinition> = ConcurrentHashMap()

    // ── Регистрация хендлеров ─────────────────────────────────────────────

    /**
     * Регистрирует хендлер способности.
     *
     * @param handler хендлер
     * @throws IllegalStateException если хендлер с таким ID уже зарегистрирован
     */
    fun register(handler: AbilityHandler) {
        if (handlers.containsKey(handler.id)) {
            throw IllegalStateException(
                "AbilityHandler '${handler.id}' is already registered"
            )
        }
        handlers[handler.id] = handler
        logger.debug("Registered ability handler: ${handler.id}")
    }

    /**
     * Регистрирует хендлер, перезаписывая существующий если есть.
     * Используй для переопределения встроенных способностей.
     */
    fun registerOrReplace(handler: AbilityHandler) {
        if (handlers.containsKey(handler.id)) {
            logger.warn("Replacing ability handler: ${handler.id}")
        }
        handlers[handler.id] = handler
        logger.debug("Registered ability handler: ${handler.id}")
    }

    // ── Регистрация Definition ────────────────────────────────────────────

    /**
     * Регистрирует Definition способности.
     * Вызывается загрузчиком датапаков.
     */
    fun registerDefinition(definition: AbilityDefinition) {
        definitions[definition.id] = definition
        logger.debug("Registered ability definition: ${definition.id}")
    }

    /**
     * Очищает все Definition (вызывается при перезагрузке датапаков).
     */
    fun clearDefinitions() {
        definitions.clear()
        logger.info("Cleared all ability definitions")
    }

    // ── Получение ─────────────────────────────────────────────────────────

    /**
     * Возвращает хендлер по ID или null.
     */
    fun getHandler(id: Identifier): AbilityHandler? = handlers[id]

    /**
     * Возвращает Definition по ID или null.
     */
    fun getDefinition(id: Identifier): AbilityDefinition? = definitions[id]

    /**
     * Возвращает пару (хендлер, definition) или null если что-то не найдено.
     */
    fun get(id: Identifier): Pair<AbilityHandler, AbilityDefinition>? {
        val handler = handlers[id] ?: return null
        val definition = definitions[id] ?: return null
        return handler to definition
    }

    /**
     * Проверяет зарегистрирован ли хендлер.
     */
    fun hasHandler(id: Identifier): Boolean = handlers.containsKey(id)

    /**
     * Проверяет загружена ли Definition.
     */
    fun hasDefinition(id: Identifier): Boolean = definitions.containsKey(id)

    /**
     * Возвращает все зарегистрированные хендлеры.
     */
    fun getAllHandlers(): Collection<AbilityHandler> = handlers.values

    /**
     * Возвращает все загруженные Definition.
     */
    fun getAllDefinitions(): Collection<AbilityDefinition> = definitions.values

    fun handlerCount(): Int = handlers.size
    fun definitionCount(): Int = definitions.size

    fun printStatistics() {
        logger.info("=== Ability Registry ===")
        logger.info("  Handlers: ${handlerCount()}")
        logger.info("  Definitions: ${definitionCount()}")
        val missing = handlers.keys.filter { !definitions.containsKey(it) }
        if (missing.isNotEmpty()) {
            logger.warn("  Handlers without definitions: $missing")
        }
        logger.info("========================")
    }
}
