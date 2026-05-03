package omc.boundbyfate.client.gui.widgets

import omc.boundbyfate.client.gui.core.*

/**
 * Чекбокс с n состояниями, каждое из которых отображается как кружок.
 *
 * Конкретная реализация [CheckboxWidget]. Вся логика состояний, кликов
 * и анимации — в базовом классе. Здесь только визуал.
 *
 * ## Использование
 * ```kotlin
 * val proficiencyStyles = listOf(
 *     CheckboxAppearance(filled = false, fillColor = 0,                  borderColor = Theme.panel.border),
 *     CheckboxAppearance(filled = true,  fillColor = Theme.text.accent,  borderColor = Theme.text.accent),
 *     CheckboxAppearance(filled = true,  fillColor = 0xFF55AA55.toInt(), borderColor = 0xFF55AA55.toInt())
 * )
 * val checkbox = CycleCheckbox(appearances = proficiencyStyles)
 * checkbox.onStateChanged = { state -> ... }
 * ```
 */
class CycleCheckbox(
    val appearances: List<CheckboxAppearance>,
    initialState: Int = 0,
    /** Доля от меньшей стороны контекста для размера кружка. */
    val sizeRatio: Float = 0.55f
) : CheckboxWidget(stateCount = appearances.size, initialState = initialState) {

    init {
        require(appearances.isNotEmpty()) { "CycleCheckbox: appearances must not be empty" }
    }

    override fun renderState(ctx: RenderContext, state: Int, scale: Float) {
        val appearance = appearances[state]
        val size = (minOf(ctx.width, ctx.height) * sizeRatio * scale).toInt().coerceAtLeast(2)

        // Центрируем кружок в контексте
        val ox = ctx.cx - size / 2
        val oy = ctx.cy - size / 2

        if (appearance.filled) {
            drawCircleFilled(ctx, ox, oy, size, appearance.fillColor, appearance.borderColor)
        } else {
            drawCircleOutline(ctx, ox, oy, size, appearance.borderColor)
        }
    }

    // ── Рисование кружка ──────────────────────────────────────────────────

    /**
     * Заполненный кружок через попиксельную проверку расстояния от центра.
     * Надёжно работает при любом размере включая маленькие (4-8px).
     */
    private fun drawCircleFilled(ctx: RenderContext, x: Int, y: Int, size: Int, fill: Int, border: Int) {
        val cx = x + size / 2f - 0.5f
        val cy = y + size / 2f - 0.5f
        val r  = size / 2f
        val r2 = r * r
        val rb = (r - 1f).coerceAtLeast(0f)
        val rb2 = rb * rb

        for (py in y until y + size) {
            for (px in x until x + size) {
                val dx = px - cx
                val dy = py - cy
                val d2 = dx * dx + dy * dy
                when {
                    d2 <= rb2 -> ctx.drawContext.fill(px, py, px + 1, py + 1, fill)
                    d2 <= r2  -> ctx.drawContext.fill(px, py, px + 1, py + 1, border)
                }
            }
        }
    }

    /**
     * Контур кружка через попиксельную проверку.
     */
    private fun drawCircleOutline(ctx: RenderContext, x: Int, y: Int, size: Int, color: Int) {
        val cx = x + size / 2f - 0.5f
        val cy = y + size / 2f - 0.5f
        val r  = size / 2f
        val r2 = r * r
        val ri = (r - 1f).coerceAtLeast(0f)
        val ri2 = ri * ri

        for (py in y until y + size) {
            for (px in x until x + size) {
                val dx = px - cx
                val dy = py - cy
                val d2 = dx * dx + dy * dy
                if (d2 in ri2..r2) {
                    ctx.drawContext.fill(px, py, px + 1, py + 1, color)
                }
            }
        }
    }
}

// ── Описание состояния ────────────────────────────────────────────────────

/**
 * Внешний вид одного состояния [CycleCheckbox].
 *
 * @param filled заполнен ли кружок
 * @param fillColor цвет заливки (игнорируется если filled = false)
 * @param borderColor цвет контура
 */
data class CheckboxAppearance(
    val filled: Boolean,
    val fillColor: Int,
    val borderColor: Int
)
