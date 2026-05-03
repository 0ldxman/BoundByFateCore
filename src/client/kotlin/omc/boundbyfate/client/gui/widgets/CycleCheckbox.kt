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
     * Заполненный кружок через пиксельную аппроксимацию (вертикальные полосы).
     */
    private fun drawCircleFilled(ctx: RenderContext, x: Int, y: Int, size: Int, fill: Int, border: Int) {
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
        drawCircleOutline(ctx, x, y, size, border)
    }

    /**
     * Контур кружка через пиксельную аппроксимацию.
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
            ctx.drawContext.fill(px, top,    px + 1, top + 1,    color)
            ctx.drawContext.fill(px, bottom, px + 1, bottom + 1, color)
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
