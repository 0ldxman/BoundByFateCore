package omc.boundbyfate.client.gui.widgets

import omc.boundbyfate.client.gui.core.*

/**
 * Простой виджет-текст. Нужен чтобы класть текст в layout-контейнеры
 * наравне с другими виджетами.
 *
 * ## Использование
 * ```kotlin
 * hbox(gap = 4) {
 *     add(TextWidget("СИЛ"), width = 30, height = rowH)
 *     add(TextWidget("+1", align = TextAlign.RIGHT, color = Theme.text.accent), width = 20, height = rowH)
 *     add(CycleCheckbox(...), width = 10, height = rowH)
 * }
 * ```
 */
class TextWidget(
    var text: String,
    var align: TextAlign = TextAlign.LEFT,
    var color: Int = -1,           // -1 = Theme.text.primary
    var scale: Float = 1f,
    var shadow: Boolean = false,
    var verticalCenter: Boolean = true
) : BbfWidget() {

    override fun tick(ctx: RenderContext) {
        tickAll(ctx.delta)
    }

    override fun render(ctx: RenderContext) {
        val resolvedColor = if (color == -1) Theme.text.primary else color

        val x = when (align) {
            TextAlign.LEFT   -> ctx.x
            TextAlign.CENTER -> ctx.cx
            TextAlign.RIGHT  -> ctx.right
        }

        val y = if (verticalCenter) {
            // Центрируем по вертикали: высота шрифта ~8px, с учётом scale
            ctx.cy - ((8f * scale) / 2f).toInt()
        } else {
            ctx.y
        }

        ctx.drawContext.drawScaledText(
            text, x, y,
            scale = scale,
            color = resolvedColor,
            align = align,
            shadow = shadow
        )
    }
}
