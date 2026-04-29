package omc.boundbyfate.client.gui.components

import omc.boundbyfate.client.gui.core.RenderContext
import omc.boundbyfate.client.gui.core.UiSounds
import omc.boundbyfate.client.gui.core.playUi

/**
 * Компонент отслеживания курсора.
 *
 * Добавляй к любому виджету для получения hover состояния.
 *
 * ## Использование
 *
 * ```kotlin
 * class MyWidget : AnimOwner() {
 *     val hover = Hoverable()
 *
 *     fun tick(ctx: RenderContext) {
 *         hover.update(ctx)
 *         scale.target = if (hover.isHovered) 1.1f else 1f
 *     }
 * }
 * ```
 */
class Hoverable(
    /** Играть ли звук при наведении. */
    var playSoundOnEnter: Boolean = true,
    /** Играть ли звук при уходе курсора. */
    var playSoundOnExit: Boolean = false
) {
    var isHovered = false
        private set

    /** Нормализованная позиция курсора внутри элемента (-1..1). */
    var normalizedX = 0f
        private set
    var normalizedY = 0f
        private set

    /** Последняя позиция курсора. */
    var lastMouseX = 0
        private set
    var lastMouseY = 0
        private set

    private var wasHovered = false

    /** true в первый кадр когда курсор вошёл. */
    val justEntered get() = isHovered && !wasHovered

    /** true в первый кадр когда курсор ушёл. */
    val justExited get() = !isHovered && wasHovered

    private val enterCallbacks = mutableListOf<() -> Unit>()
    private val exitCallbacks  = mutableListOf<() -> Unit>()
    private val moveCallbacks  = mutableListOf<(x: Float, y: Float) -> Unit>()

    fun onEnter(block: () -> Unit) { enterCallbacks += block }
    fun onExit(block: () -> Unit)  { exitCallbacks  += block }
    fun onMove(block: (x: Float, y: Float) -> Unit) { moveCallbacks += block }

    /**
     * Обновляет состояние hover.
     * Вызывай в [tick] виджета.
     */
    fun update(ctx: RenderContext) {
        update(ctx.mouseX, ctx.mouseY, ctx.x, ctx.y, ctx.width, ctx.height)
    }

    /**
     * Обновляет состояние hover с явными координатами.
     */
    fun update(mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        wasHovered = isHovered
        lastMouseX = mouseX
        lastMouseY = mouseY

        isHovered = mouseX in x..(x + w) && mouseY in y..(y + h)

        if (isHovered && w > 0 && h > 0) {
            normalizedX = ((mouseX - (x + w / 2)).toFloat() / (w / 2f)).coerceIn(-1f, 1f)
            normalizedY = ((mouseY - (y + h / 2)).toFloat() / (h / 2f)).coerceIn(-1f, 1f)
        }

        if (justEntered) {
            if (playSoundOnEnter) UiSounds.current.hover?.playUi()
            enterCallbacks.forEach { it() }
        }
        if (justExited) {
            if (playSoundOnExit) UiSounds.current.hoverEnd?.playUi()
            exitCallbacks.forEach { it() }
        }
        if (isHovered) {
            moveCallbacks.forEach { it(normalizedX, normalizedY) }
        }
    }

    fun reset() {
        isHovered = false
        wasHovered = false
        normalizedX = 0f
        normalizedY = 0f
    }
}
