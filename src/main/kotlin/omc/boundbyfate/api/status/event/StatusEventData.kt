package omc.boundbyfate.api.status.event

import net.minecraft.entity.LivingEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.api.status.ActiveStatus
import omc.boundbyfate.api.status.StatusDefinition
import omc.boundbyfate.event.core.BaseCancellableEvent
import omc.boundbyfate.util.source.SourceReference
import omc.boundbyfate.util.time.Duration

/**
 * Data-классы для событий системы состояний.
 */

// ── BEFORE_APPLY ──────────────────────────────────────────────────────────

/**
 * Событие перед применением состояния.
 *
 * Отмена полностью блокирует применение — состояние не накладывается,
 * его эффекты не применяются.
 *
 * Используй для:
 * - Иммунитетов (иммунитет к яду блокирует poisoned)
 * - Условий окружения (нельзя поджечь в воде)
 * - GM override (принудительно заблокировать состояние)
 *
 * [duration] и [source] можно изменить до применения:
 * ```kotlin
 * StatusEvents.BEFORE_APPLY.register { event ->
 *     // Сократить длительность вдвое если есть бафф
 *     if (event.entity.hasBuff(FORTITUDE)) {
 *         event.duration = Duration.seconds(event.durationSeconds / 2)
 *     }
 * }
 * ```
 */
class BeforeStatusApplyEvent(
    val entity: LivingEntity,
    val definition: StatusDefinition,
    val source: SourceReference,
    var duration: Duration
) : BaseCancellableEvent()

// ── AFTER_APPLY ───────────────────────────────────────────────────────────

/**
 * Событие после применения состояния.
 *
 * Не отменяемое — состояние уже активно.
 *
 * Используй для:
 * - Цепных реакций (poisoned → применить weakened)
 * - UI обновлений (показать иконку состояния)
 * - Логирования, достижений
 * - Уведомления игрока
 *
 * ```kotlin
 * StatusEvents.AFTER_APPLY.register { event ->
 *     if (event.definition.id == PARALYZED_ID) {
 *         // Уведомить союзников
 *         notifyAllies(event.entity, "ally_paralyzed")
 *     }
 * }
 * ```
 */
class AfterStatusApplyEvent(
    val entity: LivingEntity,
    val definition: StatusDefinition,
    val activeStatus: ActiveStatus
)

// ── BEFORE_REMOVE ─────────────────────────────────────────────────────────

/**
 * Событие перед снятием состояния.
 *
 * Отмена блокирует снятие — состояние остаётся активным,
 * его эффекты не снимаются.
 *
 * Используй для:
 * - Состояний которые нельзя снять обычными средствами
 * - Проверки условий снятия (нужен особый предмет или заклинание)
 * - GM override (запретить снятие)
 *
 * ```kotlin
 * StatusEvents.BEFORE_REMOVE.register { event ->
 *     if (event.definition.id == CURSED_ID) {
 *         if (!event.source.isSpell(REMOVE_CURSE_ID)) {
 *             event.cancel()
 *             event.entity.sendMessage("This curse cannot be removed this way")
 *         }
 *     }
 * }
 * ```
 */
class BeforeStatusRemoveEvent(
    val entity: LivingEntity,
    val definition: StatusDefinition,
    val activeStatus: ActiveStatus,
    val source: SourceReference
) : BaseCancellableEvent()

// ── AFTER_REMOVE ──────────────────────────────────────────────────────────

/**
 * Событие после снятия состояния.
 *
 * Не отменяемое — состояние уже снято.
 *
 * Используй для:
 * - Цепных реакций при снятии (снятие яда → восстановить HP)
 * - UI обновлений (убрать иконку состояния)
 * - Логирования, достижений
 *
 * ```kotlin
 * StatusEvents.AFTER_REMOVE.register { event ->
 *     if (event.definition.id == POISONED_ID) {
 *         event.entity.heal(2f) // небольшое восстановление
 *     }
 * }
 * ```
 */
class AfterStatusRemoveEvent(
    val entity: LivingEntity,
    val definition: StatusDefinition,
    val source: SourceReference
)

// ── ON_EXPIRE ─────────────────────────────────────────────────────────────

/**
 * Событие истечения состояния по таймеру.
 *
 * Отмена продлевает состояние — оно не снимается в этот тик.
 * Следующая проверка будет на следующем тике.
 *
 * Отличается от [BeforeStatusRemoveEvent] тем что срабатывает
 * только при истечении таймера, а не при явном снятии.
 *
 * Используй для:
 * - Продления состояния при определённых условиях
 * - Особого поведения при истечении (взрыв, трансформация)
 *
 * ```kotlin
 * StatusEvents.ON_EXPIRE.register { event ->
 *     if (event.definition.id == BURNING_ID) {
 *         // Поджечь блок под ногами при истечении
 *         igniteBlockBelow(event.entity)
 *     }
 * }
 * ```
 */
class OnStatusExpireEvent(
    val entity: LivingEntity,
    val definition: StatusDefinition,
    val activeStatus: ActiveStatus
) : BaseCancellableEvent()

// ── ON_DURATION_UPDATE ────────────────────────────────────────────────────

/**
 * Событие обновления длительности состояния (повторное применение).
 *
 * Срабатывает когда состояние уже активно и его пытаются применить снова.
 * Не отменяемое — обновление уже произошло.
 *
 * Используй для:
 * - Уведомления игрока об обновлении длительности
 * - Логирования
 *
 * ```kotlin
 * StatusEvents.ON_DURATION_UPDATE.register { event ->
 *     event.entity.sendMessage("${event.definition.id} duration refreshed")
 * }
 * ```
 */
class OnStatusDurationUpdateEvent(
    val entity: LivingEntity,
    val definition: StatusDefinition,
    val activeStatus: ActiveStatus,
    val newDuration: Duration
)
