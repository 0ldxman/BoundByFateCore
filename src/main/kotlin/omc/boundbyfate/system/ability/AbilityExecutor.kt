package omc.boundbyfate.system.ability

import net.minecraft.entity.LivingEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import omc.boundbyfate.api.ability.AbilityContext
import omc.boundbyfate.api.ability.AbilityHandler
import omc.boundbyfate.api.ability.AbilityUseResult
import omc.boundbyfate.api.ability.CanUseResult
import omc.boundbyfate.api.ability.event.*
import omc.boundbyfate.registry.AbilityRegistry
import org.slf4j.LoggerFactory

/**
 * Оркестратор выполнения способностей.
 *
 * Управляет полным потоком выполнения:
 * ```
 * BEFORE_CHECK → canUse → onPreparationStart → ON_PREPARATION_TICK
 *     → BEFORE_EXECUTE → execute → AFTER_EXECUTE
 * ```
 *
 * ## Использование
 *
 * ```kotlin
 * // Мгновенное выполнение (без подготовки)
 * val result = AbilityExecutor.tryExecute(caster, abilityId, world, currentTick)
 *
 * // С позицией цели (для AoE)
 * val result = AbilityExecutor.tryExecute(caster, abilityId, world, currentTick, targetPos = pos)
 *
 * // Dry-run для UI превью
 * val ctx = AbilityExecutor.dryRun(caster, abilityId, world, currentTick, targets)
 * ```
 */
object AbilityExecutor {

    private val logger = LoggerFactory.getLogger(AbilityExecutor::class.java)

    // ── Основное выполнение ───────────────────────────────────────────────

    /**
     * Пытается выполнить способность.
     *
     * @param caster кастер
     * @param abilityId ID способности
     * @param world серверный мир
     * @param currentTick текущий тик
     * @param targets предварительно собранные цели (опционально)
     * @param targetPos позиция цели для AoE (опционально)
     * @return результат попытки
     */
    fun tryExecute(
        caster: LivingEntity,
        abilityId: Identifier,
        world: ServerWorld,
        currentTick: Long,
        targets: List<LivingEntity> = emptyList(),
        targetPos: Vec3d? = null
    ): AbilityUseResult {
        // Получаем хендлер и definition
        val (handler, definition) = AbilityRegistry.get(abilityId)
            ?: return AbilityUseResult.HandlerNotFound(abilityId)

        val ctx = AbilityContext(
            caster = caster,
            definition = definition,
            world = world,
            currentTick = currentTick,
            targets = targets.toMutableList(),
            targetPos = targetPos
        )

        return execute(handler, ctx)
    }

    /**
     * Выполняет способность с готовым контекстом.
     * Используй если нужен полный контроль над контекстом.
     */
    fun execute(handler: AbilityHandler, ctx: AbilityContext): AbilityUseResult {
        // ── 1. BEFORE_CHECK ───────────────────────────────────────────────
        val checkEvent = BeforeAbilityCheckEvent(ctx)
        AbilityEvents.BEFORE_CHECK.invokeCancellable(checkEvent) {
            it.onBeforeCheck(checkEvent)
        }
        if (checkEvent.isCancelled) {
            logger.debug("Ability ${ctx.definition.id} blocked by BEFORE_CHECK")
            return AbilityUseResult.Blocked(checkEvent.blockReason)
        }

        // ── 2. canUse ─────────────────────────────────────────────────────
        val canUse = handler.canUse(ctx)
        if (canUse !is CanUseResult.Yes) {
            logger.debug("Ability ${ctx.definition.id} cannot be used: $canUse")
            return AbilityUseResult.CannotUse(canUse)
        }

        // ── 3. Подготовка (мгновенная — без тикования) ───────────────────
        // Для Charged/Channeled способностей тикование управляется отдельно
        // через AbilityPreparationManager. Здесь просто уведомляем о начале.
        handler.onPreparationStart(ctx)

        // ── 4. BEFORE_EXECUTE ─────────────────────────────────────────────
        val execEvent = BeforeAbilityExecuteEvent(ctx)
        AbilityEvents.BEFORE_EXECUTE.invokeCancellable(execEvent) {
            it.onBeforeExecute(execEvent)
        }
        if (execEvent.isCancelled) {
            logger.debug("Ability ${ctx.definition.id} cancelled by BEFORE_EXECUTE")
            return AbilityUseResult.Cancelled
        }

        // ── 5. execute ────────────────────────────────────────────────────
        try {
            handler.execute(ctx)
        } catch (e: Exception) {
            logger.error("Error executing ability ${ctx.definition.id}", e)
            return AbilityUseResult.Cancelled
        }

        // ── 6. AFTER_EXECUTE ──────────────────────────────────────────────
        val afterEvent = AfterAbilityExecuteEvent(ctx)
        AbilityEvents.AFTER_EXECUTE.invoke { it.onAfterExecute(afterEvent) }

        handler.onAfterExecute(ctx)

        logger.debug(
            "Ability ${ctx.definition.id} executed successfully. " +
            "Results: ${ctx.results.size}"
        )

        return AbilityUseResult.Success
    }

    // ── Прерывание ────────────────────────────────────────────────────────

    /**
     * Прерывает выполняющуюся способность.
     *
     * @param handler хендлер способности
     * @param ctx контекст
     * @param forced true если прерывание насильственное
     */
    fun interrupt(handler: AbilityHandler, ctx: AbilityContext, forced: Boolean = false) {
        handler.onInterrupted(ctx, forced)

        val event = AbilityInterruptedEvent(ctx, forced)
        AbilityEvents.ON_INTERRUPTED.invoke { it.onInterrupted(event) }

        logger.debug(
            "Ability ${ctx.definition.id} interrupted " +
            "(forced=$forced)"
        )
    }

    // ── Dry-run ───────────────────────────────────────────────────────────

    /**
     * Выполняет способность в режиме превью.
     *
     * Логика работает полностью, но реальных изменений нет.
     * Результаты доступны через [AbilityContext.results].
     *
     * @return контекст с заполненными results или null если хендлер не найден
     */
    fun dryRun(
        caster: LivingEntity,
        abilityId: Identifier,
        world: ServerWorld,
        currentTick: Long,
        targets: List<LivingEntity> = emptyList(),
        targetPos: Vec3d? = null
    ): AbilityContext? {
        val (handler, definition) = AbilityRegistry.get(abilityId) ?: return null

        val ctx = AbilityContext(
            caster = caster,
            definition = definition,
            world = world,
            currentTick = currentTick,
            targets = targets.toMutableList(),
            targetPos = targetPos,
            isDryRun = true
        )

        try {
            handler.execute(ctx)
        } catch (e: Exception) {
            logger.error("Error during dry-run of ability $abilityId", e)
        }

        return ctx
    }
}
