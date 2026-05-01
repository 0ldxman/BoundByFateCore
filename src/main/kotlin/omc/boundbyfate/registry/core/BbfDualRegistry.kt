package omc.boundbyfate.registry.core

import net.minecraft.util.Identifier
import omc.boundbyfate.api.core.Registrable
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Базовый класс для реестров с двумя независимыми хранилищами:
 * **хендлер** (логика, код) и **дефиниция** (данные, JSON).
 *
 * Хендлер и дефиниция связываются по одному [Identifier].
 *
 * ## Жизненный цикл
 *
 * - **Хендлеры** регистрируются один раз при старте мода и никогда не очищаются.
 * - **Дефиниции** загружаются из датапаков и очищаются при каждом `/reload`.
 *
 * ## Использование
 *
 * ```kotlin
 * object AbilityRegistry : BbfDualRegistry<AbilityHandler, AbilityDefinition>("abilities")
 * ```
 *
 * Регистрация хендлера (в коде):
 * ```kotlin
 * AbilityRegistry.registerHandler(SecondWind)
 * ```
 *
 * Регистрация дефиниции (из загрузчика датапаков):
 * ```kotlin
 * AbilityRegistry.registerDefinition(definition)
 * ```
 *
 * Получение:
 * ```kotlin
 * val handler    = AbilityRegistry.getHandler(id)
 * val definition = AbilityRegistry.getDefinition(id)
 * val (h, d)     = AbilityRegistry.get(id) ?: return  // оба сразу
 * ```
 *
 * @param H тип хендлера — логика, регистрируется в коде
 * @param D тип дефиниции — данные из JSON, должен быть [Registrable]
 * @param name имя реестра для логирования
 */
abstract class BbfDualRegistry<H : Any, D : Registrable>(val name: String) {

    protected val logger = LoggerFactory.getLogger("BbfDualRegistry[$name]")

    /** Хендлеры — логика, регистрируются при старте, не очищаются. */
    private val handlers = ConcurrentHashMap<Identifier, H>()

    /** Дефиниции — данные из JSON, очищаются при /reload. */
    private val definitions = ConcurrentHashMap<Identifier, D>()

    // ── Хендлеры ──────────────────────────────────────────────────────────

    /**
     * Регистрирует хендлер.
     *
     * @param id идентификатор хендлера
     * @param handler хендлер
     * @throws IllegalStateException если хендлер с таким ID уже зарегистрирован
     */
    fun registerHandler(id: Identifier, handler: H) {
        check(!handlers.containsKey(id)) {
            "Handler '$id' is already registered in $name"
        }
        handlers[id] = handler
        logger.debug("Registered handler: $id")
    }

    /**
     * Регистрирует хендлер, перезаписывая существующий.
     * Используй для переопределения встроенных хендлеров из другого мода.
     */
    fun registerOrReplaceHandler(id: Identifier, handler: H) {
        if (handlers.containsKey(id)) logger.warn("Replacing handler: $id in $name")
        handlers[id] = handler
    }

    fun getHandler(id: Identifier): H? = handlers[id]

    fun hasHandler(id: Identifier): Boolean = handlers.containsKey(id)

    fun getAllHandlers(): Collection<H> = handlers.values

    fun handlerCount(): Int = handlers.size

    // ── Дефиниции ─────────────────────────────────────────────────────────

    /**
     * Регистрирует дефиницию.
     * Вызывается загрузчиком датапаков — не вручную.
     */
    fun registerDefinition(definition: D) {
        definition.validate()
        definitions[definition.id] = definition
        logger.debug("Registered definition: ${definition.id}")
    }

    /**
     * Очищает все дефиниции.
     * Вызывается перед каждой перезагрузкой датапаков.
     */
    fun clearDefinitions() {
        definitions.clear()
        logger.info("Cleared definitions in $name")
    }

    fun getDefinition(id: Identifier): D? = definitions[id]

    fun hasDefinition(id: Identifier): Boolean = definitions.containsKey(id)

    fun getAllDefinitions(): Collection<D> = definitions.values

    fun definitionCount(): Int = definitions.size

    // ── Совместный доступ ─────────────────────────────────────────────────

    /**
     * Возвращает пару (хендлер, дефиниция) или null если что-то не найдено.
     *
     * ```kotlin
     * val (handler, definition) = AbilityRegistry.get(id) ?: return
     * ```
     */
    fun get(id: Identifier): Pair<H, D>? {
        val handler = handlers[id] ?: return null
        val definition = definitions[id] ?: return null
        return handler to definition
    }

    // ── Диагностика ───────────────────────────────────────────────────────

    /**
     * Выводит статистику и предупреждает о хендлерах без дефиниций.
     */
    fun printStatistics() {
        logger.info("=== $name ===")
        logger.info("  Handlers:    $handlerCount()")
        logger.info("  Definitions: $definitionCount()")
        val missing = handlers.keys.filter { !definitions.containsKey(it) }
        if (missing.isNotEmpty()) {
            logger.warn("  Handlers without definitions: $missing")
        }
        logger.info("${"=".repeat(name.length + 8)}")
    }
}
