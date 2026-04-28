package omc.boundbyfate.api.status.event

import omc.boundbyfate.event.core.EventBus
import omc.boundbyfate.event.core.eventBus

/**
 * Все EventBus точки вмешательства в систему состояний.
 *
 * ## Поток apply
 *
 * ```
 * BEFORE_APPLY         ← отменяемый, можно изменить duration
 *     ↓ (если не отменён)
 * применяем эффекты + includes
 *     ↓
 * AFTER_APPLY          ← не отменяемый, состояние активно
 * ```
 *
 * ## Поток remove (явное снятие)
 *
 * ```
 * BEFORE_REMOVE        ← отменяемый
 *     ↓ (если не отменён)
 * снимаем эффекты + orphaned includes
 *     ↓
 * AFTER_REMOVE         ← не отменяемый, состояние снято
 * ```
 *
 * ## Поток истечения по таймеру
 *
 * ```
 * ON_EXPIRE            ← отменяемый, можно продлить
 *     ↓ (если не отменён)
 * → BEFORE_REMOVE → снятие → AFTER_REMOVE
 * ```
 *
 * ## Повторное применение (стак не работает, обновляем длительность)
 *
 * ```
 * BEFORE_APPLY         ← отменяемый
 *     ↓ (если не отменён, состояние уже активно)
 * ON_DURATION_UPDATE   ← не отменяемый, длительность обновлена
 * ```
 *
 * ## Примеры
 *
 * ```kotlin
 * // Иммунитет к яду
 * StatusEvents.BEFORE_APPLY.register { event ->
 *     if (event.definition.id == POISONED_ID) {
 *         if (event.entity.hasImmunity(POISON)) {
 *             event.cancel()
 *         }
 *     }
 * }
 *
 * // Сократить длительность вдвое при наличии баффа
 * StatusEvents.BEFORE_APPLY.register { event ->
 *     if (event.entity.hasBuff(FORTITUDE)) {
 *         event.duration = Duration.seconds(
 *             (event.duration as? Duration.Ticks)?.ticks?.div(40) ?: 10
 *         )
 *     }
 * }
 *
 * // Цепная реакция: отравлен → ослаблен
 * StatusEvents.AFTER_APPLY.register { event ->
 *     if (event.definition.id == POISONED_ID) {
 *         StatusSystem.apply(event.entity, WEAKENED_ID, Duration.seconds(10), event.activeStatus.source)
 *     }
 * }
 *
 * // Проклятие нельзя снять без Remove Curse
 * StatusEvents.BEFORE_REMOVE.register { event ->
 *     if (event.definition.hasTag("curse")) {
 *         if (!event.source.isSpell(REMOVE_CURSE_ID)) {
 *             event.cancel()
 *         }
 *     }
 * }
 *
 * // Особый эффект при истечении горения
 * StatusEvents.ON_EXPIRE.register { event ->
 *     if (event.definition.id == BURNING_ID) {
 *         igniteBlockBelow(event.entity)
 *     }
 * }
 * ```
 */
object StatusEvents {

    /**
     * Перед применением состояния.
     * Отменяемый — блокирует применение.
     * [BeforeStatusApplyEvent.duration] можно изменить до применения.
     */
    val BEFORE_APPLY: EventBus<BeforeStatusApplyListener> =
        eventBus("status.before_apply")

    /**
     * После применения состояния.
     * Не отменяемый — состояние уже активно.
     */
    val AFTER_APPLY: EventBus<AfterStatusApplyListener> =
        eventBus("status.after_apply")

    /**
     * Перед снятием состояния (явное снятие или по событию).
     * Отменяемый — блокирует снятие.
     */
    val BEFORE_REMOVE: EventBus<BeforeStatusRemoveListener> =
        eventBus("status.before_remove")

    /**
     * После снятия состояния.
     * Не отменяемый — состояние уже снято.
     */
    val AFTER_REMOVE: EventBus<AfterStatusRemoveListener> =
        eventBus("status.after_remove")

    /**
     * При истечении состояния по таймеру.
     * Отменяемый — продлевает состояние на этот тик.
     */
    val ON_EXPIRE: EventBus<OnStatusExpireListener> =
        eventBus("status.on_expire")

    /**
     * При обновлении длительности (повторное применение).
     * Не отменяемый — длительность уже обновлена.
     */
    val ON_DURATION_UPDATE: EventBus<OnStatusDurationUpdateListener> =
        eventBus("status.on_duration_update")
}

// ── Callback интерфейсы ───────────────────────────────────────────────────

fun interface BeforeStatusApplyListener {
    fun onBeforeApply(event: BeforeStatusApplyEvent)
}

fun interface AfterStatusApplyListener {
    fun onAfterApply(event: AfterStatusApplyEvent)
}

fun interface BeforeStatusRemoveListener {
    fun onBeforeRemove(event: BeforeStatusRemoveEvent)
}

fun interface AfterStatusRemoveListener {
    fun onAfterRemove(event: AfterStatusRemoveEvent)
}

fun interface OnStatusExpireListener {
    fun onExpire(event: OnStatusExpireEvent)
}

fun interface OnStatusDurationUpdateListener {
    fun onDurationUpdate(event: OnStatusDurationUpdateEvent)
}
