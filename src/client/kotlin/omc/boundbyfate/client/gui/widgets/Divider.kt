package omc.boundbyfate.client.gui.widgets

import omc.boundbyfate.client.gui.core.*

/**
 * Виджет горизонтального разделителя.
 *
 * Линия рисуется по вертикальному центру контекста.
 * Высота контекста определяет суммарные отступы сверху и снизу от линии.
 *
 * Поддерживает opacity-градиент от краёв к центру через [fadeRatio].
 *
 * ## Использование
 * ```kotlin
 * // Простая линия с 2px отступами сверху/снизу
 * add(Divider(), height = 5, width = colW)
 *
 * // Линия с градиентом (30% с каждой стороны уходит в прозрачность)
 * add(Divider(fadeRatio = 0.3f), height = 5, width = colW)
 *
 * // Полный градиент от краёв до центра
 * add(Divider(fadeRatio = 0.5f), height = 5, width = colW)
 *
 * // 2px линия с горизонтальными отступами
 * add(Divider(thickness = 2, horizontalPadding = 8), height = 6, width = colW)
 * ```
 */
class Divider(
    /** Цвет линии. По умолчанию — цвет рамки из темы. */
    var color: Int = -1,
    /** Толщина линии в пикселях (обычно 1 или 2). */
    var thickness: Int = 1,
    /** Горизонтальные отступы — линия не доходит до краёв на это количество пикселей. */
    var horizontalPadding: Int = 0,
    /**
     * Доля ширины с каждой стороны где применяется fade-градиент (0..0.5).
     *   0f   — градиента нет, линия однородная
     *   0.3f — 30% с каждой стороны уходит в прозрачность
     *   0.5f — полный градиент от краёв до центра
     */
    var fadeRatio: Float = 0f
) : BbfWidget() {

    override fun tick(ctx: RenderContext) {
        tickAll(ctx.delta)
    }

    override fun render(ctx: RenderContext) {
        val resolvedColor = if (color == -1) Theme.panel.border else color

        val lineY = ctx.cy - thickness / 2
        val lineX = ctx.x + horizontalPadding
        val lineW = ctx.width - horizontalPadding * 2

        if (lineW <= 0) return

        if (fadeRatio <= 0f) {
            // Без градиента — один fillRect
            ctx.drawContext.fillRect(lineX, lineY, lineW, thickness, resolvedColor)
        } else {
            // С градиентом — попиксельный рендер по X
            val clampedFade = fadeRatio.coerceIn(0f, 0.5f)
            for (i in 0 until lineW) {
                val t = i.toFloat() / lineW
                // Расстояние от ближайшего края (0 на краях, 0.5 в центре)
                val edgeDist = minOf(t, 1f - t)
                val alpha = if (edgeDist < clampedFade) edgeDist / clampedFade else 1f
                val pixelColor = resolvedColor.withAlpha(alpha)
                ctx.drawContext.fillRect(lineX + i, lineY, 1, thickness, pixelColor)
            }
        }
    }
}
