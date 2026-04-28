package omc.boundbyfate.api.ability

import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Система модификаторов вычисляемых значений способностей.
 *
 * Модификаторы — это именованные точки перехвата внутри вычислений.
 * Способность объявляет что у неё можно изменить через [AbilityContext.modify],
 * а внешний код регистрирует обработчики на конкретные ключи.
 *
 * ## Отличие от ивентов
 *
 * - **Ивенты** — вмешательство в **ход выполнения** (отменить, добавить побочный эффект)
 * - **Модификаторы** — изменение **вычисляемых значений** (урон, радиус, количество целей)
 *
 * ## Использование
 *
 * В коде способности:
 * ```kotlin
 * override fun execute(ctx: AbilityContext) {
 *     // Объявляем модифицируемое значение
 *     val damage = ctx.modify("damage") { roll(ctx.data.requireDiceExpression("damage_dice")) }
 *     ctx.dealDamage(target, damage, DamageTypes.FIRE)
 * }
 * ```
 *
 * Регистрация модификатора (откуда угодно):
 * ```kotlin
 * // Empowered Spell — перебросить кубики урона
 * val handle = AbilityModifiers.register("damage") { base, ctx ->
 *     maxOf(base, ctx.roll(ctx.data.requireDiceExpression("damage_dice")))
 * }
 *
 * // Убрать модификатор когда он больше не нужен
 * AbilityModifiers.unregister(handle)
 * ```
 *
 * ## Порядок применения
 *
 * Модификаторы применяются в порядке регистрации.
 * Каждый получает результат предыдущего.
 */
object AbilityModifiers {

    private val logger = LoggerFactory.getLogger(AbilityModifiers::class.java)

    /**
     * Зарегистрированные модификаторы по ключу.
     * CopyOnWriteArrayList для thread-safety при итерации.
     */
    private val modifiers: MutableMap<String, CopyOnWriteArrayList<ModifierEntry>> =
        mutableMapOf()

    // ── Регистрация ───────────────────────────────────────────────────────

    /**
     * Регистрирует модификатор для ключа.
     *
     * Модификатор применяется ко всем способностям которые используют
     * этот ключ через [AbilityContext.modify].
     *
     * @param key ключ значения (например "damage", "radius", "heal_amount")
     * @param modifier функция трансформации: (текущее значение, контекст) → новое значение
     * @return handle для последующего удаления
     */
    fun register(key: String, modifier: (Int, AbilityContext) -> Int): ModifierHandle {
        val entry = ModifierEntry(key, modifier)
        modifiers.getOrPut(key) { CopyOnWriteArrayList() }.add(entry)
        logger.debug("Registered modifier for key '$key'")
        return ModifierHandle(entry)
    }

    /**
     * Регистрирует модификатор для Float значений.
     */
    fun registerFloat(key: String, modifier: (Float, AbilityContext) -> Float): FloatModifierHandle {
        val entry = FloatModifierEntry(key, modifier)
        floatModifiers.getOrPut(key) { CopyOnWriteArrayList() }.add(entry)
        logger.debug("Registered float modifier for key '$key'")
        return FloatModifierHandle(entry)
    }

    /**
     * Удаляет модификатор по handle.
     */
    fun unregister(handle: ModifierHandle) {
        modifiers[handle.entry.key]?.remove(handle.entry)
        logger.debug("Unregistered modifier for key '${handle.entry.key}'")
    }

    /**
     * Удаляет Float модификатор по handle.
     */
    fun unregister(handle: FloatModifierHandle) {
        floatModifiers[handle.entry.key]?.remove(handle.entry)
    }

    // ── Применение ────────────────────────────────────────────────────────

    /**
     * Применяет все модификаторы для ключа к значению.
     * Вызывается из [AbilityContext.modify].
     *
     * @param key ключ значения
     * @param base базовое значение
     * @param ctx контекст способности
     * @return модифицированное значение
     */
    internal fun applyInt(key: String, base: Int, ctx: AbilityContext): Int {
        val list = modifiers[key] ?: return base
        var result = base
        for (entry in list) {
            try {
                result = entry.modifier(result, ctx)
            } catch (e: Exception) {
                logger.error("Error applying modifier for key '$key'", e)
            }
        }
        return result
    }

    internal fun applyFloat(key: String, base: Float, ctx: AbilityContext): Float {
        val list = floatModifiers[key] ?: return base
        var result = base
        for (entry in list) {
            try {
                result = entry.modifier(result, ctx)
            } catch (e: Exception) {
                logger.error("Error applying float modifier for key '$key'", e)
            }
        }
        return result
    }

    // ── Проверки ──────────────────────────────────────────────────────────

    /**
     * Проверяет есть ли активные модификаторы для ключа.
     */
    fun hasModifiers(key: String): Boolean =
        (modifiers[key]?.isNotEmpty() == true) ||
        (floatModifiers[key]?.isNotEmpty() == true)

    /**
     * Очищает все модификаторы (для тестов и перезагрузки).
     */
    fun clearAll() {
        modifiers.clear()
        floatModifiers.clear()
        logger.info("Cleared all ability modifiers")
    }

    // ── Float хранилище ───────────────────────────────────────────────────

    private val floatModifiers: MutableMap<String, CopyOnWriteArrayList<FloatModifierEntry>> =
        mutableMapOf()

    // ── Внутренние классы ─────────────────────────────────────────────────

    internal data class ModifierEntry(
        val key: String,
        val modifier: (Int, AbilityContext) -> Int
    )

    internal data class FloatModifierEntry(
        val key: String,
        val modifier: (Float, AbilityContext) -> Float
    )
}

/**
 * Handle для удаления Int модификатора.
 */
@JvmInline
internal value class ModifierHandle(internal val entry: AbilityModifiers.ModifierEntry)

/**
 * Handle для удаления Float модификатора.
 */
@JvmInline
internal value class FloatModifierHandle(internal val entry: AbilityModifiers.FloatModifierEntry)
