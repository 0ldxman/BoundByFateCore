package omc.boundbyfate.api.ability.event

import omc.boundbyfate.event.core.EventBus
import omc.boundbyfate.event.core.eventBus

/**
 * Все EventBus точки вливания в систему способностей.
 *
 * ## Поток выполнения
 *
 * ```
 * BEFORE_CHECK            ← заблокировать до canUse (Silence, GM override)
 *     ↓
 * canUse()                ← логика самой способности
 *     ↓
 * onPreparationStart()
 *     ↓
 * ON_PREPARATION_TICK     ← каждый тик, Counterspell здесь
 *     ↓ (или ON_INTERRUPTED если отменили)
 * BEFORE_EXECUTE          ← последний шанс отменить, изменить контекст
 *     ↓
 * execute()
 *     ↓ (внутри execute, при контакте с целью)
 * ON_EXECUTE              ← Shield, Evasion, Lifesteal
 *     ↓
 * AFTER_EXECUTE           ← цепные реакции, побочные эффекты
 * ```
 *
 * ## Использование
 *
 * ```kotlin
 * // Counterspell — прерывает заклинание во время подготовки
 * AbilityEvents.ON_PREPARATION_TICK.register(EventPriority.HIGH) { event ->
 *     if ("spell" in event.ctx.definition.effectiveTags) {
 *         if (checkForCounterspell(event.ctx)) {
 *             event.interrupt(forced = false)
 *         }
 *     }
 * }
 *
 * // Silence — блокирует заклинания до canUse
 * AbilityEvents.BEFORE_CHECK.register { event ->
 *     if ("spell" in event.ctx.definition.effectiveTags) {
 *         if (event.ctx.caster.hasStatus(SILENCED)) {
 *             event.block(Text.translatable("status.silenced.cannot_cast"))
 *         }
 *     }
 * }
 *
 * // Lifesteal — после выполнения
 * AbilityEvents.AFTER_EXECUTE.register { event ->
 *     val totalDamage = event.ctx.results
 *         .filterIsInstance<AbilityExecutionResult.DamageDealt>()
 *         .sumOf { it.amount }
 *     if (totalDamage > 0) {
 *         event.ctx.heal(event.ctx.caster, totalDamage / 5)
 *     }
 * }
 * ```
 */
object AbilityEvents {

    /**
     * Перед проверкой canUse.
     * Отменяемый — блокирует способность до вызова canUse.
     *
     * Callback: `(BeforeAbilityCheckEvent) -> Unit`
     */
    val BEFORE_CHECK: EventBus<BeforeAbilityCheck> =
        eventBus("ability.before_check")

    /**
     * Каждый тик во время подготовки.
     * Отменяемый — прерывает подготовку.
     *
     * Callback: `(AbilityPreparationTickEvent) -> Unit`
     */
    val ON_PREPARATION_TICK: EventBus<OnAbilityPreparationTick> =
        eventBus("ability.on_preparation_tick")

    /**
     * Перед вызовом execute, после завершения подготовки.
     * Отменяемый — останавливает выполнение.
     *
     * Callback: `(BeforeAbilityExecuteEvent) -> Unit`
     */
    val BEFORE_EXECUTE: EventBus<BeforeAbilityExecute> =
        eventBus("ability.before_execute")

    /**
     * В момент контакта эффекта с конкретной целью.
     * Отменяемый для конкретной цели.
     *
     * Callback: `(AbilityExecuteOnTargetEvent) -> Unit`
     */
    val ON_EXECUTE: EventBus<OnAbilityExecute> =
        eventBus("ability.on_execute")

    /**
     * После завершения execute.
     * Не отменяемый.
     *
     * Callback: `(AfterAbilityExecuteEvent) -> Unit`
     */
    val AFTER_EXECUTE: EventBus<AfterAbilityExecute> =
        eventBus("ability.after_execute")

    /**
     * При прерывании подготовки.
     * Не отменяемый.
     *
     * Callback: `(AbilityInterruptedEvent) -> Unit`
     */
    val ON_INTERRUPTED: EventBus<OnAbilityInterrupted> =
        eventBus("ability.on_interrupted")
}

// ── Callback интерфейсы ───────────────────────────────────────────────────

fun interface BeforeAbilityCheck {
    fun onBeforeCheck(event: BeforeAbilityCheckEvent)
}

fun interface OnAbilityPreparationTick {
    fun onPreparationTick(event: AbilityPreparationTickEvent)
}

fun interface BeforeAbilityExecute {
    fun onBeforeExecute(event: BeforeAbilityExecuteEvent)
}

fun interface OnAbilityExecute {
    fun onExecute(event: AbilityExecuteOnTargetEvent)
}

fun interface AfterAbilityExecute {
    fun onAfterExecute(event: AfterAbilityExecuteEvent)
}

fun interface OnAbilityInterrupted {
    fun onInterrupted(event: AbilityInterruptedEvent)
}
