package omc.boundbyfate.client.gui.widgets

import omc.boundbyfate.client.gui.core.*

/**
 * Виджет прогресс-бара.
 *
 * Занимает весь ctx.width × ctx.height.
 * Заполнение плавно анимируется при изменении [value] если [animated] = true.
 *
 * ## Использование
 * ```kotlin
 * val xpBar = ProgressBar(value = 350f, maxValue = 1000f)
 * xpBar.value = 500f  // плавно анимируется к новому значению
 *
 * // В layout:
 * add(xpBar, height = 4, width = colW)
 * ```
 */
class ProgressBar(
    value: Float = 0f,
    var maxValue: Float = 100f,
    var fillColor: Int = -1,    // -1 = Theme.text.accent
    var bgColor: Int = -1,      // -1 = Theme.panel.background
    var borderColor: Int = -1,  // -1 = Theme.panel.border
    var animated: Boolean = true
) : BbfWidget() {

    // ── Значение ──────────────────────────────────────────────────────────

    /** Текущее значение (0..maxValue). Изменение плавно анимируется. */
    var value: Float = value.coerceIn(0f, maxValue)
        set(v) {
            field = v.coerceIn(0f, maxValue)
            if (animated) {
                fillAnim.target = field / maxValue.coerceAtLeast(0.001f)
            } else {
                fillAnim.snap(field / maxValue.coerceAtLeast(0.001f))
            }
        }

    /** Прогресс 0..1 — анимированное значение для рендера. */
    private val fillAnim = animFloat(
        initial = value.coerceIn(0f, maxValue) / maxValue.coerceAtLeast(0.001f),
        speed = 0.12f
    )

    // ── Tick ──────────────────────────────────────────────────────────────

    override fun tick(ctx: RenderContext) {
        tickAll(ctx.delta)
    }

    // ── Render ────────────────────────────────────────────────────────────

    override fun render(ctx: RenderContext) {
        val resolvedFill   = if (fillColor   == -1) Theme.text.accent       else fillColor
        val resolvedBg     = if (bgColor     == -1) Theme.panel.background  else bgColor
        val resolvedBorder = if (borderColor == -1) Theme.panel.border      else borderColor

        // Фон + рамка
        ctx.drawContext.fillRectWithBorder(
            ctx.x, ctx.y, ctx.width, ctx.height,
            bg = resolvedBg, border = resolvedBorder, thickness = 1
        )

        // Заполнение
        val fillW = ((ctx.width - 2) * fillAnim.current).toInt().coerceAtLeast(0)
        if (fillW > 0) {
            ctx.drawContext.fillRect(ctx.x + 1, ctx.y + 1, fillW, ctx.height - 2, resolvedFill)
        }
    }
}
