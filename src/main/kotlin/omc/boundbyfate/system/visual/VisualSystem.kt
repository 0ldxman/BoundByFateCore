package omc.boundbyfate.system.visual

import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import omc.boundbyfate.api.visual.VisualContext
import omc.boundbyfate.system.visual.builder.VisualBuilder
import org.slf4j.LoggerFactory

/**
 * Главная система визуала — единая точка входа.
 *
 * Выполняет три функции:
 * 1. **Удобный API** — DSL через [VisualBuilder] для вызова подсистем
 * 2. **Оркестрация** — поддержка задержек через [VisualBuilder.delay]
 * 3. **Синхронизация** — делегирует подсистемам которые сами отправляют пакеты
 *
 * Сам по себе не содержит логики визуальных эффектов — только делегирует.
 *
 * ## Использование
 *
 * ```kotlin
 * // Простой вызов
 * VisualSystem.visual(world, pos) {
 *     particles("magic_sparkle") {
 *         circle(pos, radius = 2.0)
 *     }
 *     sound("spell_cast") {
 *         at(pos)
 *     }
 * }
 *
 * // С задержкой
 * VisualSystem.visual(world, pos) {
 *     particles("charge_up") { circle(pos, radius = 1.0) }
 *     delay(2f) {
 *         beam("laser") { from(caster.pos).to(target.pos) }
 *         sound("laser_fire") { at(pos) }
 *     }
 * }
 * ```
 *
 * ## Extension функция
 *
 * Для удобства можно использовать extension функцию [visual] прямо на [ServerWorld]:
 *
 * ```kotlin
 * world.visual(pos) {
 *     particles("explosion") { sphere(pos, radius = 3.0) }
 * }
 * ```
 */
object VisualSystem {

    private val logger = LoggerFactory.getLogger(VisualSystem::class.java)

    /**
     * Выполняет визуальный эффект.
     *
     * @param world серверный мир
     * @param pos позиция центра эффекта
     * @param sourceId ID источника (для отладки), например ID способности
     * @param block DSL блок с описанием эффектов
     */
    fun visual(
        world: ServerWorld,
        pos: Vec3d,
        sourceId: Identifier? = null,
        block: VisualBuilder.() -> Unit
    ) {
        val ctx = VisualContext(world, pos, sourceId)
        val builder = VisualBuilder(ctx)
        builder.block()

        // Обрабатываем отложенные действия (delay)
        val delayed = builder.getDelayedActions()
        if (delayed.isNotEmpty()) {
            VisualOrchestrator.schedule(world.server, delayed, ctx)
        }
    }
}

// ── Extension функции ─────────────────────────────────────────────────────

/**
 * Extension функция для удобного вызова визуальных эффектов прямо на [ServerWorld].
 *
 * ```kotlin
 * world.visual(pos) {
 *     particles("magic_sparkle") { circle(pos, radius = 2.0) }
 * }
 * ```
 */
fun ServerWorld.visual(
    pos: Vec3d,
    sourceId: Identifier? = null,
    block: VisualBuilder.() -> Unit
) = VisualSystem.visual(this, pos, sourceId, block)
