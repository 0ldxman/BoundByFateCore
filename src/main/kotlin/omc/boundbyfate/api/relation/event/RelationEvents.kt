package omc.boundbyfate.api.relation.event

import omc.boundbyfate.event.core.EventBus
import omc.boundbyfate.event.core.eventBus

/**
 * EventBus точки вмешательства в систему отношений.
 *
 * ## Поток shift
 *
 * ```
 * BEFORE_SHIFT      ← отменяемый, можно изменить delta
 *     ↓
 * изменяем значение
 *     ↓
 * AFTER_SHIFT       ← не отменяемый
 *     ↓ (если статус изменился)
 * ON_STATUS_CHANGE  ← не отменяемый
 * ```
 *
 * ## Примеры
 *
 * ```kotlin
 * // Дипломатия снижает штрафы к репутации
 * RelationEvents.BEFORE_SHIFT.register { event ->
 *     if (event.delta < 0) {
 *         val diplomacy = getSkillLevel(event.from, DIPLOMACY)
 *         event.delta = (event.delta * (1f - diplomacy * 0.05f)).toInt()
 *     }
 * }
 *
 * // Уведомить игрока об изменении репутации
 * RelationEvents.AFTER_SHIFT.register { event ->
 *     val charParty = event.from as? RelationParty.Character ?: return@register
 *     val orgParty  = event.to   as? RelationParty.Organization ?: return@register
 *     notifyPlayer(charParty.uuid, "Репутация в ${orgParty.id}: ${event.delta:+d}")
 * }
 *
 * // Цепная реакция: репутация в Гильдии Воров влияет на Стражу
 * RelationEvents.AFTER_SHIFT.register { event ->
 *     val charParty = event.from as? RelationParty.Character ?: return@register
 *     val orgParty  = event.to   as? RelationParty.Organization ?: return@register
 *     if (orgParty.id == THIEVES_GUILD_ID && event.delta > 0) {
 *         RelationSystem.shift(event.world, charParty, CITY_GUARD_PARTY, -event.delta / 2, "Связь с Гильдией Воров")
 *     }
 * }
 * ```
 */
object RelationEvents {

    /**
     * Перед изменением значения отношения.
     * Отменяемый — блокирует изменение.
     * [BeforeRelationShiftEvent.delta] можно изменить.
     */
    val BEFORE_SHIFT: EventBus<BeforeRelationShiftListener> =
        eventBus("relation.before_shift")

    /**
     * После изменения значения отношения.
     * Не отменяемый.
     */
    val AFTER_SHIFT: EventBus<AfterRelationShiftListener> =
        eventBus("relation.after_shift")

    /**
     * При изменении статуса отношения (NEUTRAL → HOSTILE и т.д.).
     * Не отменяемый. Срабатывает только если статус действительно изменился.
     */
    val ON_STATUS_CHANGE: EventBus<RelationStatusChangedListener> =
        eventBus("relation.on_status_change")

    /**
     * При добавлении тега к отношению.
     * Не отменяемый.
     */
    val ON_TAG_ADDED: EventBus<RelationTagAddedListener> =
        eventBus("relation.on_tag_added")

    /**
     * При удалении тега из отношения.
     * Не отменяемый.
     */
    val ON_TAG_REMOVED: EventBus<RelationTagRemovedListener> =
        eventBus("relation.on_tag_removed")
}

// ── Callback интерфейсы ───────────────────────────────────────────────────

fun interface BeforeRelationShiftListener {
    fun onBeforeShift(event: BeforeRelationShiftEvent)
}

fun interface AfterRelationShiftListener {
    fun onAfterShift(event: AfterRelationShiftEvent)
}

fun interface RelationStatusChangedListener {
    fun onStatusChanged(event: RelationStatusChangedEvent)
}

fun interface RelationTagAddedListener {
    fun onTagAdded(event: RelationTagAddedEvent)
}

fun interface RelationTagRemovedListener {
    fun onTagRemoved(event: RelationTagRemovedEvent)
}
