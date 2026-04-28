package omc.boundbyfate.system.effect

import net.minecraft.entity.LivingEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.api.effect.EffectContext
import omc.boundbyfate.api.effect.EffectHandler
import omc.boundbyfate.api.effect.event.AfterEffectApplyEvent
import omc.boundbyfate.api.effect.event.AfterEffectRemoveEvent
import omc.boundbyfate.api.effect.event.BeforeEffectApplyEvent
import omc.boundbyfate.api.effect.event.BeforeEffectRemoveEvent
import omc.boundbyfate.api.effect.event.EffectEvents
import omc.boundbyfate.api.effect.event.OnEffectTickEvent
import omc.boundbyfate.registry.EffectRegistry
import omc.boundbyfate.util.source.SourceReference
import org.slf4j.LoggerFactory

/**
 * Фасад для применения и снятия эффектов.
 *
 * Связывает [EffectHandler] с [EffectContext] и публикует события
 * через [EffectEvents] на каждом этапе.
 *
 * ## Поток apply
 *
 * ```
 * EffectEvents.BEFORE_APPLY  ← отменяемый
 *     ↓ (если не отменён)
 * handler.apply(ctx)
 *     ↓
 * EffectEvents.AFTER_APPLY
 * ```
 *
 * ## Поток remove
 *
 * ```
 * EffectEvents.BEFORE_REMOVE ← отменяемый
 *     ↓ (если не отменён)
 * handler.remove(ctx)
 *     ↓
 * EffectEvents.AFTER_REMOVE
 * ```
 *
 * ## Поток tick
 *
 * ```
 * EffectEvents.ON_TICK       ← отменяемый (пропускает тик)
 *     ↓ (если не отменён)
 * handler.tick(ctx)
 * ```
 *
 * ## Использование
 *
 * ```kotlin
 * // Применить по ID
 * EffectApplier.apply(entity, Identifier.of("boundbyfate-core", "darkvision"), source)
 *
 * // Применить с готовым контекстом
 * val ctx = EffectContext.passive(entity, definition, source)
 * EffectApplier.apply(handler, ctx)
 *
 * // Снять
 * EffectApplier.remove(entity, Identifier.of("boundbyfate-core", "darkvision"), source)
 * ```
 */
object EffectApplier {

    private val logger = LoggerFactory.getLogger(EffectApplier::class.java)

    // ── Применение по ID ──────────────────────────────────────────────────

    /**
     * Применяет эффект по ID.
     *
     * Ищет хендлер и definition в [EffectRegistry].
     *
     * @param entity сущность
     * @param effectId ID эффекта
     * @param source источник эффекта
     * @param trigger триггер применения
     * @return true если эффект был применён (не отменён событием)
     */
    fun apply(
        entity: LivingEntity,
        effectId: Identifier,
        source: SourceReference,
        trigger: String = EffectContext.TRIGGER_PASSIVE
    ): Boolean {
        val (handler, definition) = EffectRegistry.get(effectId) ?: run {
            logger.warn("Effect '$effectId' not found in registry")
            return false
        }

        val ctx = EffectContext(
            entity = entity,
            definition = definition,
            source = source,
            trigger = trigger
        )

        return apply(handler, ctx)
    }

    /**
     * Снимает эффект по ID.
     *
     * @return true если эффект был снят (не отменён событием)
     */
    fun remove(
        entity: LivingEntity,
        effectId: Identifier,
        source: SourceReference,
        trigger: String = EffectContext.TRIGGER_PASSIVE
    ): Boolean {
        val (handler, definition) = EffectRegistry.get(effectId) ?: run {
            logger.warn("Effect '$effectId' not found in registry")
            return false
        }

        val ctx = EffectContext(
            entity = entity,
            definition = definition,
            source = source,
            trigger = trigger
        )

        return remove(handler, ctx)
    }

    // ── Применение с готовым контекстом ──────────────────────────────────

    /**
     * Применяет эффект с готовым контекстом.
     *
     * Публикует [EffectEvents.BEFORE_APPLY] (отменяемый) и [EffectEvents.AFTER_APPLY].
     *
     * @return true если эффект был применён (не отменён событием и без ошибок)
     */
    fun apply(handler: EffectHandler, ctx: EffectContext): Boolean {
        // BEFORE_APPLY — можно отменить
        val beforeEvent = BeforeEffectApplyEvent(handler, ctx)
        EffectEvents.BEFORE_APPLY.invokeCancellable(beforeEvent) {
            it.onBeforeApply(beforeEvent)
        }

        if (beforeEvent.isCancelled) {
            logger.debug(
                "Effect '${handler.id}' apply cancelled by BEFORE_APPLY " +
                "on '${ctx.entity.name.string}'"
            )
            return false
        }

        // Вызываем логику хендлера
        return try {
            handler.apply(ctx)

            logger.debug(
                "Applied effect '${handler.id}' to '${ctx.entity.name.string}' " +
                "from '${ctx.source}'"
            )

            // AFTER_APPLY — не отменяемый
            val afterEvent = AfterEffectApplyEvent(handler, ctx)
            EffectEvents.AFTER_APPLY.invoke { it.onAfterApply(afterEvent) }

            true
        } catch (e: Exception) {
            logger.error(
                "Error applying effect '${handler.id}' to '${ctx.entity.name.string}'",
                e
            )
            false
        }
    }

    /**
     * Снимает эффект с готовым контекстом.
     *
     * Публикует [EffectEvents.BEFORE_REMOVE] (отменяемый) и [EffectEvents.AFTER_REMOVE].
     *
     * @return true если эффект был снят (не отменён событием и без ошибок)
     */
    fun remove(handler: EffectHandler, ctx: EffectContext): Boolean {
        // BEFORE_REMOVE — можно отменить
        val beforeEvent = BeforeEffectRemoveEvent(handler, ctx)
        EffectEvents.BEFORE_REMOVE.invokeCancellable(beforeEvent) {
            it.onBeforeRemove(beforeEvent)
        }

        if (beforeEvent.isCancelled) {
            logger.debug(
                "Effect '${handler.id}' remove cancelled by BEFORE_REMOVE " +
                "on '${ctx.entity.name.string}'"
            )
            return false
        }

        // Вызываем логику хендлера
        return try {
            handler.remove(ctx)

            logger.debug(
                "Removed effect '${handler.id}' from '${ctx.entity.name.string}'"
            )

            // AFTER_REMOVE — не отменяемый
            val afterEvent = AfterEffectRemoveEvent(handler, ctx)
            EffectEvents.AFTER_REMOVE.invoke { it.onAfterRemove(afterEvent) }

            true
        } catch (e: Exception) {
            logger.error(
                "Error removing effect '${handler.id}' from '${ctx.entity.name.string}'",
                e
            )
            false
        }
    }

    // ── Применение списков ────────────────────────────────────────────────

    /**
     * Применяет список эффектов по ID.
     */
    fun applyAll(
        entity: LivingEntity,
        effectIds: List<Identifier>,
        source: SourceReference,
        trigger: String = EffectContext.TRIGGER_PASSIVE
    ) = effectIds.forEach { apply(entity, it, source, trigger) }

    /**
     * Снимает список эффектов по ID.
     */
    fun removeAll(
        entity: LivingEntity,
        effectIds: List<Identifier>,
        source: SourceReference
    ) = effectIds.forEach { remove(entity, it, source) }

    // ── Тик ──────────────────────────────────────────────────────────────

    /**
     * Тикует эффект.
     *
     * Публикует [EffectEvents.ON_TICK] (отменяемый — пропускает тик).
     * Проверяет [EffectHandler.tickInterval] перед вызовом [EffectHandler.tick].
     *
     * @param handler хендлер эффекта
     * @param ctx контекст с актуальным [EffectContext.ticksActive]
     */
    fun tick(handler: EffectHandler, ctx: EffectContext) {
        if (!handler.isTicking) return
        if (ctx.ticksActive % handler.tickInterval != 0) return

        // ON_TICK — можно пропустить этот тик
        val tickEvent = OnEffectTickEvent(handler, ctx)
        EffectEvents.ON_TICK.invokeCancellable(tickEvent) {
            it.onTick(tickEvent)
        }

        if (tickEvent.isCancelled) {
            logger.debug(
                "Effect '${handler.id}' tick skipped by ON_TICK " +
                "on '${ctx.entity.name.string}' (tick ${ctx.ticksActive})"
            )
            return
        }

        try {
            handler.tick(ctx)
        } catch (e: Exception) {
            logger.error(
                "Error ticking effect '${handler.id}' on '${ctx.entity.name.string}'",
                e
            )
        }
    }
}
