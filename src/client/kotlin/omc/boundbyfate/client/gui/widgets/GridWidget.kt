package omc.boundbyfate.client.gui.widgets

import omc.boundbyfate.client.gui.core.BbfWidget
import omc.boundbyfate.client.gui.core.RenderContext

/**
 * Универсальная сетка виджетов.
 *
 * Размещает виджеты в сетке с заданным количеством колонок.
 *
 * ## Использование
 *
 * ```kotlin
 * val stats = listOf(
 *     AbilityScoreWidget("СИЛ", 10),
 *     AbilityScoreWidget("ЛОВ", 12),
 *     AbilityScoreWidget("ВЫН", 14)
 * )
 * val grid = GridWidget(
 *     widgets = stats,
 *     columns = 2,
 *     gap = 8
 * )
 * ```
 */
class GridWidget(
    var widgets: List<BbfWidget> = emptyList(),
    var columns: Int = 2,
    var gap: Int = 8,
    var itemWidth: Int = 65,  // 43 + 11 + 11 для AbilityScoreWidget
    var itemHeight: Int = 43
) : BbfWidget() {

    override fun tick(ctx: RenderContext) {
        widgets.forEachIndexed { index, widget ->
            val col = index % columns
            val row = index / columns
            val x = col * (itemWidth + gap)
            val y = row * (itemHeight + gap)

            val childCtx = ctx.child(x, y, itemWidth, itemHeight)
            widget.tick(childCtx)
        }
        tickAll(ctx.delta)
    }

    override fun render(ctx: RenderContext) {
        widgets.forEachIndexed { index, widget ->
            val col = index % columns
            val row = index / columns
            val x = col * (itemWidth + gap)
            val y = row * (itemHeight + gap)

            val childCtx = ctx.child(x, y, itemWidth, itemHeight)
            widget.render(childCtx)
        }
    }

    fun handleClick(mouseX: Int, mouseY: Int, button: Int): Boolean {
        widgets.forEachIndexed { index, widget ->
            val col = index % columns
            val row = index / columns
            val x = col * (itemWidth + gap)
            val y = row * (itemHeight + gap)

            if (mouseX in x..(x + itemWidth) && mouseY in y..(y + itemHeight)) {
                if (widget is omc.boundbyfate.client.gui.widgets.character.AbilityScoreWidget) {
                    if (widget.handleClick(mouseX - x, mouseY - y, button)) return true
                }
            }
        }
        return false
    }

    /** Общая высота сетки. */
    val totalHeight: Int
        get() {
            val rows = (widgets.size + columns - 1) / columns
            return rows * itemHeight + (rows - 1).coerceAtLeast(0) * gap
        }

    /** Общая ширина сетки. */
    val totalWidth: Int
        get() = columns * itemWidth + (columns - 1) * gap
}
