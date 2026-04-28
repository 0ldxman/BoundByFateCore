package omc.boundbyfate.api.ability

import net.minecraft.text.Text
import net.minecraft.util.Identifier

/**
 * Базовый класс логики способности.
 *
 * Каждая способность — это `object` наследующий [AbilityHandler].
 * Обязателен только [id] и [execute]. Всё остальное — опциональные хуки.
 *
 * ## Создание способности
 *
 * ```kotlin
 * object SecondWind : AbilityHandler() {
 *     override val id = Identifier("boundbyfate-core", "second_wind")
 *
 *     override fun canUse(ctx: AbilityContext): CanUseResult {
 *         if (!ctx.hasAction(ActionSlotType.BONUS_ACTION))
 *             return CanUseResult.No(Text.translatable("ability.no_bonus_action"))
 *         return CanUseResult.Yes
 *     }
 *
 *     override fun execute(ctx: AbilityContext) {
 *         val heal = ctx.roll(ctx.data.requireDiceExpression("heal_dice")) + ctx.casterLevel
 *         ctx.heal(ctx.caster, heal)
 *         ctx.consumeAction()
 *         ctx.startRecovery()
 *     }
 * }
 * ```
 *
 * ## Хуки выполнения
 *
 * ```
 * canUse()               — проверка возможности использования
 * onPreparationStart()   — начало подготовки (анимация, партиклы)
 * onPreparationTick()    — каждый тик подготовки
 * execute()              — основная логика ← ОБЯЗАТЕЛЕН
 * onAfterExecute()       — после выполнения
 * onInterrupted()        — прерывание (forced = насильственное)
 * buildDescription()     — динамическое описание для UI
 * ```
 *
 * ## Ивенты
 *
 * Помимо хуков, каждый этап публикует ивент через [AbilityEvents].
 * Это позволяет внешним системам вмешиваться без изменения кода способности.
 */
abstract class AbilityHandler {

    /**
     * Уникальный идентификатор способности.
     * Должен совпадать с `id` в JSON файле.
     */
    abstract val id: Identifier

    // ── Обязательный метод ────────────────────────────────────────────────

    /**
     * Основная логика способности.
     *
     * Вызывается после успешной проверки [canUse] и завершения подготовки.
     * Здесь происходит всё: нанесение урона, исцеление, применение статусов.
     *
     * Используй extension-функции на [AbilityContext] для типичных операций:
     * - `ctx.roll(dice)` — бросок кубиков
     * - `ctx.dealDamage(target, amount, type)` — нанести урон
     * - `ctx.heal(target, amount)` — исцелить
     * - `ctx.applyStatus(target, id, duration)` — применить статус
     * - `ctx.consumeAction()` — потратить действие
     * - `ctx.consumeResource(id, amount)` — потратить ресурс
     */
    abstract fun execute(ctx: AbilityContext)

    // ── Опциональные хуки ─────────────────────────────────────────────────

    /**
     * Проверяет можно ли использовать способность.
     *
     * Вызывается после [AbilityEvents.BEFORE_CHECK].
     * Возвращай [CanUseResult.No] с причиной — она показывается игроку.
     *
     * ```kotlin
     * override fun canUse(ctx: AbilityContext): CanUseResult {
     *     if (!ctx.hasResource(KI_POINTS, 2))
     *         return CanUseResult.No(Text.translatable("ability.not_enough_ki"))
     *     return CanUseResult.Yes
     * }
     * ```
     */
    open fun canUse(ctx: AbilityContext): CanUseResult = CanUseResult.Yes

    /**
     * Вызывается в начале подготовки.
     *
     * Используй для запуска анимаций и визуальных эффектов подготовки.
     * Ресурсы здесь НЕ тратятся — только в [execute].
     */
    open fun onPreparationStart(ctx: AbilityContext) {}

    /**
     * Вызывается каждый тик во время подготовки.
     *
     * @param ticksElapsed сколько тиков прошло с начала подготовки
     *
     * Используй для:
     * - Нарастающих визуальных эффектов
     * - Накопления заряда (сохраняй в ctx.stash)
     * - Проверки условий канала (движение прерывает и т.д.)
     */
    open fun onPreparationTick(ctx: AbilityContext, ticksElapsed: Int) {}

    /**
     * Вызывается после завершения [execute].
     *
     * Используй для cleanup, дополнительных эффектов.
     * К этому моменту [ctx.results] уже заполнен.
     */
    open fun onAfterExecute(ctx: AbilityContext) {}

    /**
     * Вызывается при прерывании подготовки.
     *
     * По умолчанию — полная отмена без последствий.
     * Переопредели для кастомного поведения (дебафф, частичный эффект).
     *
     * @param forced true если прерывание насильственное (урон, оглушение),
     *               false если мягкое (игрок сам отменил, Counterspell)
     */
    open fun onInterrupted(ctx: AbilityContext, forced: Boolean) {}

    /**
     * Строит динамическое описание способности для UI.
     *
     * По умолчанию возвращает статичный ключ локализации.
     * Переопредели для описания с числами из JSON:
     *
     * ```kotlin
     * override fun buildDescription(definition: AbilityDefinition): Text {
     *     val dice = definition.abilityData.getDiceExpression("damage_dice")
     *     return Text.translatable("ability.fireball.description", dice.toString())
     * }
     * ```
     */
    open fun buildDescription(definition: AbilityDefinition): Text =
        Text.translatable(definition.getDescriptionKey())

    override fun toString(): String = "AbilityHandler($id)"
}

