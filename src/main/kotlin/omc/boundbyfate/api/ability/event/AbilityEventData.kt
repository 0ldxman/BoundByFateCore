package omc.boundbyfate.api.ability.event

import net.minecraft.entity.LivingEntity
import net.minecraft.text.Text
import omc.boundbyfate.api.ability.AbilityContext
import omc.boundbyfate.event.core.BaseCancellableEvent

/**
 * Data-классы для событий системы способностей.
 *
 * Каждый класс несёт [ctx] — контекст выполнения способности.
 * Отменяемые события наследуют [BaseCancellableEvent].
 */

// ── BEFORE_CHECK ──────────────────────────────────────────────────────────

/**
 * Событие перед проверкой [omc.boundbyfate.api.ability.AbilityHandler.canUse].
 *
 * Отмена блокирует способность до вызова canUse.
 * [blockReason] показывается игроку если задан.
 *
 * Используй для:
 * - Глобальных блокировок (статус Silenced запрещает заклинания)
 * - GM override (принудительно разрешить или запретить)
 */
class BeforeAbilityCheckEvent(
    val ctx: AbilityContext
) : BaseCancellableEvent() {
    var blockReason: Text? = null

    fun block(reason: Text? = null) {
        cancel()
        blockReason = reason
    }
}

// ── ON_PREPARATION_TICK ───────────────────────────────────────────────────

/**
 * Событие каждого тика подготовки.
 *
 * Отмена прерывает подготовку.
 * [forced] = true означает насильственное прерывание.
 *
 * Используй для:
 * - Counterspell — прервать заклинание
 * - Проверки условий канала (движение, урон)
 */
class AbilityPreparationTickEvent(
    val ctx: AbilityContext,
    val ticksElapsed: Int
) : BaseCancellableEvent() {
    var forced: Boolean = false

    fun interrupt(forced: Boolean = false) {
        cancel()
        this.forced = forced
    }
}

// ── BEFORE_EXECUTE ────────────────────────────────────────────────────────

/**
 * Событие перед вызовом [omc.boundbyfate.api.ability.AbilityHandler.execute].
 *
 * Отмена останавливает выполнение после завершения подготовки.
 *
 * Используй для:
 * - Последнего шанса отменить
 * - Изменения контекста (добавить цели, изменить targetPos)
 */
class BeforeAbilityExecuteEvent(
    val ctx: AbilityContext
) : BaseCancellableEvent()

// ── ON_EXECUTE ────────────────────────────────────────────────────────────

/**
 * Событие в момент контакта эффекта с конкретной целью.
 *
 * Отмена для конкретной цели — эффект не применяется к ней.
 *
 * Используй для:
 * - Shield — перехватить урон до применения
 * - Evasion — полностью уклониться от AoE
 * - Absorb Elements — поглотить стихийный урон
 * - Lifesteal — зафиксировать сколько урона будет нанесено
 *
 * @property ctx контекст способности
 * @property target цель к которой применяется эффект
 */
class AbilityExecuteOnTargetEvent(
    val ctx: AbilityContext,
    val target: LivingEntity
) : BaseCancellableEvent()

// ── AFTER_EXECUTE ─────────────────────────────────────────────────────────

/**
 * Событие после завершения [omc.boundbyfate.api.ability.AbilityHandler.execute].
 *
 * Не отменяемое — всё уже произошло.
 * [ctx.results] содержит полный список того что произошло.
 *
 * Используй для:
 * - Цепных реакций (цель умерла → взрыв снова)
 * - Lifesteal — восстановить HP на основе нанесённого урона
 * - Achievement системы, GM лог
 * - Побочных эффектов не трогая код способности
 */
class AfterAbilityExecuteEvent(
    val ctx: AbilityContext
)

// ── ON_INTERRUPTED ────────────────────────────────────────────────────────

/**
 * Событие прерывания подготовки.
 *
 * Не отменяемое — прерывание уже произошло.
 *
 * @property ctx контекст способности
 * @property forced true если прерывание насильственное (урон, оглушение),
 *                  false если мягкое (игрок сам отменил, Counterspell)
 *
 * Используй для:
 * - Остановки анимаций и партиклов
 * - Дебаффов при насильственном прерывании
 * - Логирования
 */
class AbilityInterruptedEvent(
    val ctx: AbilityContext,
    val forced: Boolean
)
