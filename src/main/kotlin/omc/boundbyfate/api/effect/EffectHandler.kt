package omc.boundbyfate.api.effect

import net.minecraft.util.Identifier

/**
 * Базовый класс логики эффекта.
 *
 * Каждый эффект — это `object` наследующий [EffectHandler].
 * Обязателен только [id] и [apply]. Всё остальное — опциональные хуки.
 *
 * ## Создание эффекта
 *
 * ```kotlin
 * object Darkvision : EffectHandler() {
 *     override val id = Identifier("boundbyfate-core", "darkvision")
 *
 *     override fun apply(ctx: EffectContext) {
 *         val range = ctx.data.getInt("range", 60)
 *         ctx.putStash("range", range)
 *         // Факт активности хранится в EntityEffectsData
 *     }
 *
 *     override fun remove(ctx: EffectContext) {
 *         // Снятие через EffectApplier.remove()
 *     }
 * }
 * ```
 *
 * ## Длящийся эффект (тикующий)
 *
 * ```kotlin
 * object Poison : EffectHandler() {
 *     override val id = Identifier("boundbyfate-core", "poison")
 *
 *     // Тикует каждые 20 тиков (1 секунда)
 *     override val tickInterval: Int = 20
 *
 *     override fun apply(ctx: EffectContext) {
 *         // Сохраняем начальные данные
 *         ctx.putStash("damage", ctx.data.getInt("damage_per_tick", 1))
 *     }
 *
 *     override fun tick(ctx: EffectContext) {
 *         val damage = ctx.getStash<Int>("damage") ?: return
 *         ctx.entity.damage(ctx.entity.damageSources.magic(), damage.toFloat())
 *     }
 *
 *     override fun remove(ctx: EffectContext) {
 *         // Cleanup если нужен
 *     }
 * }
 * ```
 *
 * ## Хуки
 *
 * ```
 * apply()   — эффект применяется (обязателен)
 * remove()  — эффект снимается
 * tick()    — каждые tickInterval тиков (только если tickInterval > 0)
 * ```
 *
 * ## Регистрация
 *
 * ```kotlin
 * // В BbfEffects.register():
 * EffectRegistry.register(Darkvision)
 * EffectRegistry.register(Poison)
 * ```
 */
abstract class EffectHandler {

    /**
     * Уникальный идентификатор эффекта.
     * Должен совпадать с `id` в JSON файле.
     */
    abstract val id: Identifier

    /**
     * Интервал тикования в тиках.
     *
     * 0 — эффект не тикует (мгновенный или постоянный без тиков).
     * 20 — тикует раз в секунду.
     * 1 — тикует каждый тик.
     *
     * Если > 0, система будет вызывать [tick] каждые [tickInterval] тиков
     * пока эффект активен.
     */
    open val tickInterval: Int = 0

    /**
     * Является ли эффект длящимся.
     * true если [tickInterval] > 0.
     */
    val isTicking: Boolean get() = tickInterval > 0

    // ── Обязательный хук ──────────────────────────────────────────────────

    /**
     * Применяет эффект к сущности.
     *
     * Вызывается когда эффект начинает действовать.
     * Используй extension-функции на [EffectContext] для типичных операций.
     */
    abstract fun apply(ctx: EffectContext)

    // ── Опциональные хуки ─────────────────────────────────────────────────

    /**
     * Снимает эффект с сущности.
     *
     * Вызывается когда эффект заканчивается (истёк, снят принудительно).
     * Используй для отмены изменений сделанных в [apply].
     */
    open fun remove(ctx: EffectContext) {}

    /**
     * Тик эффекта.
     *
     * Вызывается каждые [tickInterval] тиков пока эффект активен.
     * Вызывается только если [tickInterval] > 0.
     *
     * [EffectContext.ticksActive] содержит сколько тиков эффект уже активен.
     * [EffectContext.stash] сохраняется между тиками — используй для состояния.
     *
     * ```kotlin
     * override fun tick(ctx: EffectContext) {
     *     // Нарастающий урон
     *     val multiplier = (ctx.ticksActive / 20).coerceAtMost(5)
     *     ctx.entity.damage(ctx.entity.damageSources.magic(), multiplier.toFloat())
     * }
     * ```
     */
    open fun tick(ctx: EffectContext) {}

    override fun toString(): String = "EffectHandler($id)"
}

