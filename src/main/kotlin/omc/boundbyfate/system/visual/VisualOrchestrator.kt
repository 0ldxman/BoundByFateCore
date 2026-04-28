package omc.boundbyfate.system.visual

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import omc.boundbyfate.api.visual.VisualContext
import omc.boundbyfate.system.visual.builder.VisualBuilder
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Оркестратор визуальных эффектов.
 *
 * Отвечает за выполнение отложенных действий из [VisualBuilder.delay].
 * Работает на серверном тике — каждый тик проверяет очередь и выполняет
 * действия у которых истекла задержка.
 *
 * Регистрируется один раз при инициализации мода через [register].
 */
object VisualOrchestrator {

    private val logger = LoggerFactory.getLogger(VisualOrchestrator::class.java)

    /**
     * Очередь отложенных действий.
     * CopyOnWriteArrayList для безопасности при итерации + добавлении из тика.
     */
    private val queue = CopyOnWriteArrayList<ScheduledVisual>()

    /**
     * Регистрирует тиковый обработчик.
     * Вызывается один раз при инициализации мода.
     */
    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            tick(server)
        }
        logger.info("VisualOrchestrator registered")
    }

    /**
     * Добавляет отложенные действия в очередь.
     *
     * @param server сервер для получения текущего тика
     * @param actions список отложенных действий из [VisualBuilder]
     * @param ctx контекст выполнения
     */
    fun schedule(
        server: MinecraftServer,
        actions: List<VisualBuilder.DelayedAction>,
        ctx: VisualContext
    ) {
        val currentTick = server.ticks
        for (action in actions) {
            queue.add(
                ScheduledVisual(
                    executeTick = currentTick + action.delayTicks,
                    ctx = ctx,
                    block = action.block
                )
            )
        }
    }

    /**
     * Тиковый обработчик — выполняет действия у которых истекла задержка.
     */
    private fun tick(server: MinecraftServer) {
        if (queue.isEmpty()) return

        val currentTick = server.ticks
        val toExecute = queue.filter { it.executeTick <= currentTick }

        if (toExecute.isEmpty()) return

        queue.removeAll(toExecute.toSet())

        for (scheduled in toExecute) {
            try {
                val builder = VisualBuilder(scheduled.ctx)
                builder.apply(scheduled.block)

                // Рекурсивно обрабатываем вложенные delay
                val nested = builder.getDelayedActions()
                if (nested.isNotEmpty()) {
                    schedule(server, nested, scheduled.ctx)
                }
            } catch (e: Exception) {
                logger.error("Error executing delayed visual action", e)
            }
        }
    }

    /**
     * Запланированное визуальное действие.
     *
     * @param executeTick тик сервера на котором нужно выполнить
     * @param ctx контекст выполнения
     * @param block DSL блок для выполнения
     */
    private data class ScheduledVisual(
        val executeTick: Int,
        val ctx: VisualContext,
        val block: VisualBuilder.() -> Unit
    )
}
