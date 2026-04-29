package omc.boundbyfate.client.gui.core

/**
 * Базовый класс для объектов с анимируемыми состояниями.
 *
 * Автоматически собирает все [AnimState] созданные через фабричные методы
 * и тикает их разом через [tickAll].
 *
 * ## Использование
 *
 * ```kotlin
 * class StatCard : AnimOwner() {
 *     val scale  = animFloat(1f, speed = 0.15f)
 *     val tiltX  = animFloat(0f, speed = 0.15f)
 *     val alpha  = animFloat(0f, speed = 0.1f)
 *
 *     fun tick(delta: Float) {
 *         scale.target = if (hovered) 1.25f else 1f
 *         tickAll(delta)  // одна строка вместо трёх
 *     }
 * }
 * ```
 */
abstract class AnimOwner {

    private val states = mutableListOf<AnimState<*>>()

    // ── Фабричные методы — регистрируют AnimState автоматически ──────────────

    protected fun animFloat(initial: Float, speed: Float = 0.15f): AnimState<Float> =
        omc.boundbyfate.client.gui.core.animFloat(initial, speed).also { states += it }

    protected fun animInt(initial: Int, speed: Float = 0.15f): AnimState<Int> =
        omc.boundbyfate.client.gui.core.animInt(initial, speed).also { states += it }

    protected fun animColor(initial: Int, speed: Float = 0.15f): AnimState<Int> =
        omc.boundbyfate.client.gui.core.animColor(initial, speed).also { states += it }

    protected fun animVec2(initialX: Float, initialY: Float, speed: Float = 0.15f): AnimState<Pair<Float, Float>> =
        omc.boundbyfate.client.gui.core.animVec2(initialX, initialY, speed).also { states += it }

    // ── Управление ────────────────────────────────────────────────────────────

    /**
     * Тикает все зарегистрированные [AnimState].
     * Вызывай один раз в конце [tick].
     */
    open fun tickAll(delta: Float) = states.forEach { it.tick(delta) }

    /**
     * Мгновенно сбрасывает все [AnimState] к их текущим target значениям.
     * Полезно при `init()` экрана.
     */
    fun snapAll() = states.forEach { it.snapToTarget() }
}
