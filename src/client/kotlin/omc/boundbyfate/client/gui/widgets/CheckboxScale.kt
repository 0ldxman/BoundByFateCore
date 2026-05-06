package omc.boundbyfate.client.gui.widgets

import omc.boundbyfate.client.gui.components.Clickable
import omc.boundbyfate.client.gui.components.Hoverable
import omc.boundbyfate.client.gui.core.*

/**
 * Шкала из n дискретных чекбоксов в ряд.
 *
 * Логика клика:
 *   - Клик на незакрашенный (i >= value) → value = i + 1 (зажигаем все до i включительно)
 *   - Клик на закрашенный  (i < value)  → value = i     (тушим все начиная с i)
 *
 * Внешний вид задаётся через два [CheckboxAppearance]:
 *   [filledAppearance]  — для закрашенных (value > i)
 *   [emptyAppearance]   — для пустых      (value <= i)
 *
 * ## Использование
 * ```kotlin
 * // Жизненная сила персонажа (5 очков)
 * val lifeForce = CheckboxScale(
 *     count = 5,
 *     value = 5,
 *     filledAppearance = CheckboxAppearance(filled = true,  fillColor = 0xFFFF5555.toInt(), borderColor = 0xFFFF5555.toInt()),
 *     emptyAppearance  = CheckboxAppearance(filled = false, fillColor = 0,                  borderColor = Theme.panel.border)
 * )
 * lifeForce.onValueChanged = { newValue -> character.lifeForce = newValue }
 * ```
 */
class CheckboxScale(
    val count: Int,
    value: Int = 0,
    var filledAppearance: CheckboxAppearance,
    var emptyAppearance: CheckboxAppearance,
    /** Зазор между кружками в пикселях. */
    var gap: Int = 2,
    /** Доля от размера кружка для радиуса. */
    var sizeRatio: Float = 0.8f
) : BbfWidget() {

    init {
        require(count > 0) { "CheckboxScale: count must be > 0" }
    }

    // ── Значение ──────────────────────────────────────────────────────────

    var value: Int = value.coerceIn(0, count)
        private set

    var onValueChanged: ((Int) -> Unit)? = null

    // ── Поведение ─────────────────────────────────────────────────────────

    private val hovers  = List(count) { Hoverable(playSoundOnEnter = false) }
    private val clicks  = List(count) { Clickable() }

    init {
        clicks.forEachIndexed { i, click ->
            click.onClick {
                val newValue = if (i < this.value) i else i + 1
                this.value = newValue.coerceIn(0, count)
                onValueChanged?.invoke(this.value)
            }
        }
    }

    // ── Tick ──────────────────────────────────────────────────────────────

    override fun tick(ctx: RenderContext) {
        val cellSize = cellSize(ctx)
        val totalW   = count * cellSize + (count - 1) * gap
        val startX   = ctx.cx - totalW / 2

        for (i in 0 until count) {
            val cx = startX + i * (cellSize + gap)
            hovers[i].update(ctx.mouseX, ctx.mouseY, cx, ctx.cy - cellSize / 2, cellSize, cellSize)
        }
        tickAll(ctx.delta)
    }

    // ── Render ────────────────────────────────────────────────────────────

    override fun render(ctx: RenderContext) {
        val cellSize = cellSize(ctx)
        val size     = (cellSize * sizeRatio).toInt().coerceAtLeast(2)
        val totalW   = count * cellSize + (count - 1) * gap
        val startX   = ctx.cx - totalW / 2

        for (i in 0 until count) {
            val cx = startX + i * (cellSize + gap)
            val cy = ctx.cy - cellSize / 2
            val ox = cx + (cellSize - size) / 2
            val oy = cy + (cellSize - size) / 2

            val appearance = if (i < value) filledAppearance else emptyAppearance
            drawCircle(ctx, ox, oy, size, appearance)
        }
    }

    // ── Клики ─────────────────────────────────────────────────────────────

    fun handleClick(mouseX: Int, mouseY: Int, button: Int): Boolean {
        var handled = false
        clicks.forEachIndexed { i, click ->
            if (click.handle(mouseX, mouseY, button, hovers[i].isHovered)) handled = true
        }
        return handled
    }

    // ── Вспомогательные ───────────────────────────────────────────────────

    /** Размер одной ячейки (квадрат). */
    private fun cellSize(ctx: RenderContext): Int {
        val availableW = ctx.width - (count - 1) * gap
        return minOf(availableW / count, ctx.height)
    }

    private fun drawCircle(ctx: RenderContext, x: Int, y: Int, size: Int, appearance: CheckboxAppearance) {
        val cx  = x + size / 2f - 0.5f
        val cy  = y + size / 2f - 0.5f
        val r   = size / 2f
        val r2  = r * r
        val rb  = (r - 1f).coerceAtLeast(0f)
        val rb2 = rb * rb

        for (py in y until y + size) {
            for (px in x until x + size) {
                val dx = px - cx
                val dy = py - cy
                val d2 = dx * dx + dy * dy
                if (appearance.filled) {
                    when {
                        d2 <= rb2 -> ctx.drawContext.fill(px, py, px + 1, py + 1, appearance.fillColor)
                        d2 <= r2  -> ctx.drawContext.fill(px, py, px + 1, py + 1, appearance.borderColor)
                    }
                } else {
                    val ri  = (r - 1f).coerceAtLeast(0f)
                    val ri2 = ri * ri
                    if (d2 in ri2..r2) {
                        ctx.drawContext.fill(px, py, px + 1, py + 1, appearance.borderColor)
                    }
                }
            }
        }
    }
}
