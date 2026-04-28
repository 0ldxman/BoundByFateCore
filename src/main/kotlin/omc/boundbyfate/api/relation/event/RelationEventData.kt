package omc.boundbyfate.api.relation.event

import net.minecraft.server.world.ServerWorld
import omc.boundbyfate.api.relation.Relation
import omc.boundbyfate.api.relation.RelationKey
import omc.boundbyfate.api.relation.RelationParty
import omc.boundbyfate.api.relation.RelationStatus
import omc.boundbyfate.event.core.BaseCancellableEvent

/**
 * Data-классы для событий системы отношений.
 */

// ── BEFORE_SHIFT ──────────────────────────────────────────────────────────

/**
 * Событие перед изменением значения отношения.
 *
 * Отмена блокирует изменение.
 * [delta] можно изменить — например уменьшить штраф вдвое.
 *
 * ```kotlin
 * RelationEvents.BEFORE_SHIFT.register { event ->
 *     // Дипломатический навык снижает штрафы к репутации
 *     if (event.delta < 0 && hasDiplomacySkill(event.from)) {
 *         event.delta = (event.delta * 0.5).toInt()
 *     }
 * }
 * ```
 */
class BeforeRelationShiftEvent(
    val world: ServerWorld,
    val key: RelationKey,
    val before: Relation,
    var delta: Int,
    val description: String
) : BaseCancellableEvent() {
    val from: RelationParty get() = key.from
    val to: RelationParty get() = key.to
}

// ── AFTER_SHIFT ───────────────────────────────────────────────────────────

/**
 * Событие после изменения значения отношения.
 *
 * Не отменяемое — изменение уже произошло.
 *
 * Используй для:
 * - Пересчёта рангов при изменении репутации
 * - Уведомления игрока об изменении репутации
 * - Цепных реакций (репутация в одной орг влияет на другую)
 * - Логирования
 */
class AfterRelationShiftEvent(
    val world: ServerWorld,
    val key: RelationKey,
    val before: Relation,
    val after: Relation,
    val delta: Int,
    val description: String
) {
    val from: RelationParty get() = key.from
    val to: RelationParty get() = key.to

    /** Статус изменился? */
    val statusChanged: Boolean get() = before.status != after.status
    val previousStatus: RelationStatus get() = before.status
    val newStatus: RelationStatus get() = after.status
}

// ── ON_STATUS_CHANGE ──────────────────────────────────────────────────────

/**
 * Событие изменения статуса отношения (NEUTRAL → HOSTILE и т.д.).
 *
 * Не отменяемое — статус уже изменился.
 *
 * Используй для:
 * - Уведомлений ("Гильдия Воров теперь враждебна к вам")
 * - Изменения поведения НПС
 * - Квестовых триггеров
 */
class RelationStatusChangedEvent(
    val world: ServerWorld,
    val key: RelationKey,
    val previousStatus: RelationStatus,
    val newStatus: RelationStatus,
    val relation: Relation
) {
    val from: RelationParty get() = key.from
    val to: RelationParty get() = key.to
}

// ── ON_TAG_ADDED ──────────────────────────────────────────────────────────

/**
 * Событие добавления тега к отношению.
 *
 * Не отменяемое.
 *
 * Используй для:
 * - Уведомлений ("Заключён торговый договор")
 * - Квестовых триггеров
 */
class RelationTagAddedEvent(
    val world: ServerWorld,
    val key: RelationKey,
    val tag: String,
    val relation: Relation
) {
    val from: RelationParty get() = key.from
    val to: RelationParty get() = key.to
}

// ── ON_TAG_REMOVED ────────────────────────────────────────────────────────

/**
 * Событие удаления тега из отношения.
 *
 * Не отменяемое.
 */
class RelationTagRemovedEvent(
    val world: ServerWorld,
    val key: RelationKey,
    val tag: String,
    val relation: Relation
) {
    val from: RelationParty get() = key.from
    val to: RelationParty get() = key.to
}
