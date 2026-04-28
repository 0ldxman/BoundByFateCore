package omc.boundbyfate.api.effect.event

import omc.boundbyfate.event.core.EventBus
import omc.boundbyfate.event.core.eventBus

/**
 * Все EventBus точки вмешательства в систему эффектов.
 *
 * ## Поток выполнения apply
 *
 * ```
 * BEFORE_APPLY    ← отменяемый, блокирует применение
 *     ↓
 * handler.apply() ← логика самого эффекта
 *     ↓
 * AFTER_APPLY     ← не отменяемый, эффект уже применён
 * ```
 *
 * ## Поток выполнения remove
 *
 * ```
 * BEFORE_REMOVE   ← отменяемый, блокирует снятие
 *     ↓
 * handler.remove() ← логика самого эффекта
 *     ↓
 * AFTER_REMOVE    ← не отменяемый, эффект уже снят
 * ```
 *
 * ## Поток выполнения tick
 *
 * ```
 * ON_TICK         ← отменяемый, пропускает этот тик
 *     ↓
 * handler.tick()  ← логика тика
 * ```
 *
 * ## Примеры использования
 *
 * ```kotlin
 * // Иммунитет к яду
 * EffectEvents.BEFORE_APPLY.register { event ->
 *     if (event.handler.id == POISON_ID) {
 *         if (event.ctx.entity.hasImmunity(POISON)) {
 *             event.cancel()
 *         }
 *     }
 * }
 *
 * // Проклятие нельзя снять без Remove Curse
 * EffectEvents.BEFORE_REMOVE.register { event ->
 *     if (event.handler.hasTag("curse")) {
 *         if (!event.ctx.source.isSpell(REMOVE_CURSE_ID)) {
 *             event.cancel()
 *         }
 *     }
 * }
 *
 * // Antitoxin снижает урон от яда
 * EffectEvents.ON_TICK.register { event ->
 *     if (event.handler.id == POISON_ID) {
 *         if (event.ctx.entity.hasItem(ANTITOXIN)) {
 *             event.cancel() // пропускаем каждый второй тик
 *         }
 *     }
 * }
 *
 * // Цепная реакция: яд применён → применить слабость
 * EffectEvents.AFTER_APPLY.register { event ->
 *     if (event.handler.id == POISON_ID) {
 *         StatusSystem.apply(event.ctx.entity, WEAKENED_ID, Duration.seconds(10), event.ctx.source)
 *     }
 * }
 * ```
 */
object EffectEvents {

    /**
     * Перед применением эффекта.
     * Отменяемый — блокирует вызов [omc.boundbyfate.api.effect.EffectHandler.apply].
     */
    val BEFORE_APPLY: EventBus<BeforeEffectApplyListener> =
        eventBus("effect.before_apply")

    /**
     * После применения эффекта.
     * Не отменяемый — эффект уже применён.
     */
    val AFTER_APPLY: EventBus<AfterEffectApplyListener> =
        eventBus("effect.after_apply")

    /**
     * Перед снятием эффекта.
     * Отменяемый — блокирует вызов [omc.boundbyfate.api.effect.EffectHandler.remove].
     */
    val BEFORE_REMOVE: EventBus<BeforeEffectRemoveListener> =
        eventBus("effect.before_remove")

    /**
     * После снятия эффекта.
     * Не отменяемый — эффект уже снят.
     */
    val AFTER_REMOVE: EventBus<AfterEffectRemoveListener> =
        eventBus("effect.after_remove")

    /**
     * Каждый тик длящегося эффекта.
     * Отменяемый — пропускает этот конкретный тик.
     */
    val ON_TICK: EventBus<OnEffectTickListener> =
        eventBus("effect.on_tick")
}

// ── Callback интерфейсы ───────────────────────────────────────────────────

fun interface BeforeEffectApplyListener {
    fun onBeforeApply(event: BeforeEffectApplyEvent)
}

fun interface AfterEffectApplyListener {
    fun onAfterApply(event: AfterEffectApplyEvent)
}

fun interface BeforeEffectRemoveListener {
    fun onBeforeRemove(event: BeforeEffectRemoveEvent)
}

fun interface AfterEffectRemoveListener {
    fun onAfterRemove(event: AfterEffectRemoveEvent)
}

fun interface OnEffectTickListener {
    fun onTick(event: OnEffectTickEvent)
}
