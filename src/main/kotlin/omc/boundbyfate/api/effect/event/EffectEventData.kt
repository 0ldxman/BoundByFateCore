package omc.boundbyfate.api.effect.event

import omc.boundbyfate.api.effect.EffectContext
import omc.boundbyfate.api.effect.EffectHandler
import omc.boundbyfate.event.core.BaseCancellableEvent

/**
 * Data-классы для событий системы эффектов.
 *
 * Каждый класс несёт [ctx] — контекст применения эффекта,
 * и [handler] — хендлер который будет/был вызван.
 */

// ── BEFORE_APPLY ──────────────────────────────────────────────────────────

/**
 * Событие перед применением эффекта.
 *
 * Отмена полностью блокирует применение — [EffectHandler.apply] не вызывается.
 *
 * Используй для:
 * - Иммунитетов (иммунитет к яду блокирует poison)
 * - Сопротивлений (Spell Resistance снижает шанс применения)
 * - GM override (принудительно заблокировать эффект)
 * - Логирования попыток применения
 *
 * ```kotlin
 * EffectEvents.BEFORE_APPLY.register { event ->
 *     if (event.handler.id == POISON_ID) {
 *         if (event.ctx.entity.hasImmunity(POISON)) {
 *             event.cancel()
 *         }
 *     }
 * }
 * ```
 */
class BeforeEffectApplyEvent(
    val handler: EffectHandler,
    val ctx: EffectContext
) : BaseCancellableEvent()

// ── AFTER_APPLY ───────────────────────────────────────────────────────────

/**
 * Событие после применения эффекта.
 *
 * Не отменяемое — эффект уже применён.
 *
 * Используй для:
 * - Цепных реакций (применение яда → применить слабость)
 * - UI обновлений (показать иконку состояния)
 * - Логирования
 * - Achievement системы
 *
 * ```kotlin
 * EffectEvents.AFTER_APPLY.register { event ->
 *     if (event.handler.id == POISON_ID) {
 *         // Показать частицы яда
 *         spawnPoisonParticles(event.ctx.entity)
 *     }
 * }
 * ```
 */
class AfterEffectApplyEvent(
    val handler: EffectHandler,
    val ctx: EffectContext
)

// ── BEFORE_REMOVE ─────────────────────────────────────────────────────────

/**
 * Событие перед снятием эффекта.
 *
 * Отмена блокирует снятие — [EffectHandler.remove] не вызывается.
 *
 * Используй для:
 * - Проклятий которые нельзя снять без Remove Curse
 * - Эффектов которые требуют особых условий для снятия
 * - GM override (запретить снятие)
 *
 * ```kotlin
 * EffectEvents.BEFORE_REMOVE.register { event ->
 *     if (event.handler.hasTag("curse")) {
 *         if (!event.ctx.source.isSpell(REMOVE_CURSE_ID)) {
 *             event.cancel()
 *             event.ctx.entity.sendMessage("This curse cannot be removed this way")
 *         }
 *     }
 * }
 * ```
 */
class BeforeEffectRemoveEvent(
    val handler: EffectHandler,
    val ctx: EffectContext
) : BaseCancellableEvent()

// ── AFTER_REMOVE ──────────────────────────────────────────────────────────

/**
 * Событие после снятия эффекта.
 *
 * Не отменяемое — эффект уже снят.
 *
 * Используй для:
 * - Цепных реакций при снятии (снятие яда → восстановить 1 HP)
 * - UI обновлений (убрать иконку состояния)
 * - Логирования
 *
 * ```kotlin
 * EffectEvents.AFTER_REMOVE.register { event ->
 *     if (event.handler.id == POISON_ID) {
 *         // Небольшое восстановление после снятия яда
 *         event.ctx.entity.heal(1f)
 *     }
 * }
 * ```
 */
class AfterEffectRemoveEvent(
    val handler: EffectHandler,
    val ctx: EffectContext
)

// ── ON_TICK ───────────────────────────────────────────────────────────────

/**
 * Событие каждого тика длящегося эффекта.
 *
 * Отмена пропускает этот конкретный тик — [EffectHandler.tick] не вызывается.
 * Эффект продолжает висеть, следующий тик будет вызван в штатном режиме.
 *
 * Используй для:
 * - Снижения урона от тикующих эффектов (Antitoxin уменьшает урон яда)
 * - Пропуска тика при определённых условиях
 * - Модификации поведения тика
 *
 * ```kotlin
 * EffectEvents.ON_TICK.register { event ->
 *     if (event.handler.id == POISON_ID) {
 *         if (event.ctx.entity.hasItem(ANTITOXIN)) {
 *             // Пропускаем каждый второй тик — урон вдвое меньше
 *             if (event.ctx.ticksActive % 40 != 0) {
 *                 event.cancel()
 *             }
 *         }
 *     }
 * }
 * ```
 */
class OnEffectTickEvent(
    val handler: EffectHandler,
    val ctx: EffectContext
) : BaseCancellableEvent()
