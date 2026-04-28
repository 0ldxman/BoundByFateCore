package omc.boundbyfate.system.visual.builder

import net.minecraft.particle.ParticleEffect
import net.minecraft.util.Identifier
import omc.boundbyfate.api.visual.VisualContext

/**
 * DSL билдер для системы визуала.
 *
 * Единственная точка входа для вызова всех визуальных подсистем.
 * Не содержит логики — только делегирует в подсистемы.
 *
 * ## Использование
 *
 * ```kotlin
 * visual(world, pos) {
 *     particles(ParticleTypes.END_ROD) {
 *         line(from, to, count = 20)
 *         circle(pos, radius = 2.0)
 *     }
 *     sound("spell_cast") {
 *         at(pos, volume = 0.8f)
 *     }
 *     delay(2f) {
 *         particles(ParticleTypes.FLAME) { sphere(pos, radius = 3.0) }
 *         sound("boom") { at(pos) }
 *     }
 * }
 * ```
 */
class VisualBuilder(val ctx: VisualContext) {

    private val delayedActions = mutableListOf<DelayedAction>()

    /**
     * Вызывает подсистему партиклов.
     *
     * Принимает любой [ParticleEffect] — ванильный или из другого мода.
     *
     * ```kotlin
     * particles(ParticleTypes.END_ROD) {
     *     line(from, to, count = 20)
     *     circle(center, radius = 2.0)
     * }
     *
     * // Партикл с параметрами (цвет, размер)
     * particles(DustParticleEffect(Vec3f(1f, 0f, 0f), 1.5f)) {
     *     sphere(pos, radius = 2.0, count = 40)
     * }
     * ```
     */
    fun particles(particle: ParticleEffect, block: ParticleBuilder.() -> Unit) {
        val builder = ParticleBuilder(particle, ctx)
        builder.block()
        builder.execute()
    }

    /**
     * Вызывает подсистему звуков.
     *
     * ```kotlin
     * sound("spell_cast") {
     *     at(pos, volume = 0.8f, pitch = 1.2f)
     * }
     * ```
     */
    fun sound(id: String, block: SoundBuilder.() -> Unit) {
        sound(Identifier("boundbyfate-core", id), block)
    }

    fun sound(id: Identifier, block: SoundBuilder.() -> Unit) {
        val builder = SoundBuilder(id, ctx)
        builder.block()
        builder.execute()
    }

    /**
     * Откладывает выполнение блока на [seconds] секунд.
     *
     * ```kotlin
     * delay(2f) {
     *     particles(ParticleTypes.EXPLOSION) { at(pos) }
     *     sound("boom") { at(pos) }
     * }
     * ```
     */
    fun delay(seconds: Float, block: VisualBuilder.() -> Unit) {
        val ticks = (seconds * 20).toInt()
        delayedActions.add(DelayedAction(ticks, block))
    }

    internal fun getDelayedActions(): List<DelayedAction> = delayedActions

    data class DelayedAction(
        val delayTicks: Int,
        val block: VisualBuilder.() -> Unit
    )
}

