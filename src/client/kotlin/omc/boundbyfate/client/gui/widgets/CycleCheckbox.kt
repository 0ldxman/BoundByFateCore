package omc.boundbyfate.client.gui.widgets

import omc.boundbyfate.client.gui.components.Clickable
import omc.boundbyfate.client.gui.components.Hoverable
import omc.boundbyfate.client.gui.core.*

/**
 * Виджет-чекбокс с n состояниями. Клик циклически переключает состояние.
 *
 * Внешний вид каждого состояния задаётся через [CheckboxAppearance].
 * Размер кружка = min(ctx.width, ctx.height) * [sizeRatio], центрируется в контексте.
 *
 * ## Использование
 * ```kotlin
 * val proficiencyStyles = listOf(
 *     CheckboxAppearance(filled = false, fillColor = 0,                  borderColor = Theme.panel.border),
 *     CheckboxAppearance(filled = true,  fillColor = Theme.text.accent,  borderColor = Theme.text.accent),
 *     CheckboxAppearance(filled = true,  fillColor = 0xFF55AA55.toInt(), borderColor = 0xFF55AA55.toInt())
 * )
 * val checkbox = CycleCheckbox(stateCount = 3, appearances = proficiencyStyles)
 * checkbox.onStateChanged = { state -> ... }
 * ```
 */
class CycleCheckbox(
    val stateCount: Int,
    val appearances: List<CheckboxAppearance>,
    initialState: Int = 0,
    /** Доля от меньшей стороны контекста для размера кружка. */
    val sizeRatio: Float = 0.55f
) : BbfWidget() {

    init {
        require(appearances.size >= stateCount) {
            "CycleCheckbox: appearances.size (${appearances.size}) < stateCount ($stateCount)"
        }
    }

    var currentState: Int = initialState
        private set

    var onStateChanged: ((Int) -> Unit)? = null

    // ── Поведение ─────────────────────────────────────────────────────────

    private val hover = Hoverable()
    private val click = Clickable()

    // ── Анимация ──────────────────────────────────────────────────────────

    private val scaleAnim = animFloat(1f, speed = 0.25f)

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

    override fun render(ctx: RenderContext) {
        val appearance = appearances[currentState]
        val size = (minOf(ctx.width, ctx.height) * sizeRatio * scaleAnim.current).toInt().coerceAtLeast(2)

        // Центрируем кружок в контексте
        val cx = ctx.cx - size / 2
        val cy = ctx.cy - size / 2

        if (appearance.filled) {
            // Заполненный кружок — имитируем через fillRect с border-radius эффектом
            drawCircle(ctx, cx, cy, size, appearance.fillColor, appearance.borderColor)
        } else {
            // Пустой кружок — только рамка
            drawCircleOutline(ctx, cx, cy, size, appearance.borderColor)
        }
    }

    // ── Клики ─────────────────────────────────────────────────────────────

    fun handleClick(mouseX: Int, mouseY: Int, button: Int): Boolean =
        click.handle(mouseX, mouseY, button, hover.isHovered)

    // ── Рисование кружка ──────────────────────────────────────────────────

    /**
     * Рисует заполненный кружок через пиксельную аппроксимацию.
     * Minecraft не имеет нативного drawCircle, поэтому используем
     * вертикальные полосы по алгоритму средней точки.
     */
    private fun drawCircle(ctx: RenderContext, x: Int, y: Int, size: Int, fill: Int, border: Int) {
        val r = size / 2f
        val cx = x + r
        val cy = y + r

        for (px in x until x + size) {
            val dx = px - cx + 0.5f
            val halfH = Math.sqrt((r * r - dx * dx).toDouble().coerceAtLeast(0.0)).toFloat()
            val top    = (cy - halfH).toInt()
            val bottom = (cy + halfH).toInt()
            ctx.drawContext.fill(px, top, px + 1, bottom + 1, fill)
        }

        // Рамка поверх
        drawCircleOutline(ctx, x, y, size, border)
    }

    /**
     * Рисует контур кружка через пиксельную аппроксимацию.
     */
    private fun drawCircleOutline(ctx: RenderContext, x: Int, y: Int, size: Int, color: Int) {
        val r = size / 2f
        val cx = x + r
        val cy = y + r

        for (px in x until x + size) {
            val dx = px - cx + 0.5f
            val halfH = Math.sqrt((r * r - dx * dx).toDouble().coerceAtLeast(0.0)).toFloat()
            val top    = (cy - halfH).toInt()
            val bottom = (cy + halfH).toInt()
            ctx.drawContext.fill(px, top,     px + 1, top + 1,    color)
            ctx.drawContext.fill(px, bottom,  px + 1, bottom + 1, color)
        }
    }
}

/**
 * Описание внешнего вида одного состояния чекбокса.
 *
 * @param filled заполнен ли кружок
 * @param fillColor цвет заливки (игнорируется если filled = false)
 * @param borderColor цвет рамки/контура
 */
data class CheckboxAppearance(
    val filled: Boolean,
    val fillColor: Int,
    val borderColor: Int
)
