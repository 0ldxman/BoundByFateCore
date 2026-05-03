package omc.boundbyfate.client.gui.widgets

import omc.boundbyfate.client.gui.components.Clickable
import omc.boundbyfate.client.gui.components.Hoverable
import omc.boundbyfate.client.gui.core.*

/**
 * Абстрактный базовый класс для всех чекбоксов.
 *
 * Содержит всю общую логику: состояния, клики, hover, анимацию масштаба.
 * Дочерний класс реализует только один метод — [renderState] — визуал конкретного состояния.
 *
 * ## Создание своего чекбокса
 * ```kotlin
 * class TickCheckbox(val checkedColor: Int) : CheckboxWidget(stateCount = 2) {
 *     override fun renderState(ctx: RenderContext, state: Int, scale: Float) {
 *         if (state == 1) drawTick(ctx, checkedColor)
 *         else drawEmptyBox(ctx)
 *     }
 * }
 * ```
 */
abstract class CheckboxWidget(
    val stateCount: Int,
    initialState: Int = 0
) : BbfWidget() {

    // ── Состояние ─────────────────────────────────────────────────────────

    var currentState: Int = initialState
        private set

    var onStateChanged: ((Int) -> Unit)? = null

    // ── Поведение ─────────────────────────────────────────────────────────

    protected val hover = Hoverable()
    private val click = Clickable()

    // ── Анимация ──────────────────────────────────────────────────────────

    protected val scaleAnim = animFloat(1f, speed = 0.25f)

    init {
        click.onClick {
            currentState = (currentState + 1) % stateCount
            onStateChanged?.invoke(currentState)
        }
    }

    // ── Tick ──────────────────────────────────────────────────────────────

    override fun tick(ctx: RenderContext) {
        hover.update(ctx)
        scaleAnim.target = if (hover.isHovered) 1.15f else 1f
        tickAll(ctx.delta)
    }

    // ── Render ────────────────────────────────────────────────────────────

    final override fun render(ctx: RenderContext) {
        renderState(ctx, currentState, scaleAnim.current)
    }

    /**
     * Рисует визуал для конкретного состояния.
     *
     * @param ctx контекст рендера — виджет занимает весь ctx.width × ctx.height
     * @param state текущее состояние (0..stateCount-1)
     * @param scale текущий масштаб из hover-анимации (применяй через [DrawContext.transform])
     */
    abstract fun renderState(ctx: RenderContext, state: Int, scale: Float)

    // ── Управление состоянием ─────────────────────────────────────────────

    /** Устанавливает состояние без вызова колбека. */
    fun setState(state: Int) {
        currentState = state.coerceIn(0, stateCount - 1)
    }

    /** Устанавливает состояние с вызовом колбека. */
    fun setStateAndNotify(state: Int) {
        setState(state)
        onStateChanged?.invoke(currentState)
    }

    // ── Клики (пробрасываются из экрана) ──────────────────────────────────

    fun handleClick(mouseX: Int, mouseY: Int, button: Int): Boolean =
        click.handle(mouseX, mouseY, button, hover.isHovered)
}
