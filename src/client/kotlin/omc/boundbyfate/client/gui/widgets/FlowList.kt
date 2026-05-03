package omc.boundbyfate.client.gui.widgets

import omc.boundbyfate.client.gui.core.*

/**
 * Типизированный динамический список виджетов с настраиваемым направлением потока.
 *
 * Наследует [BaseList] — управление элементами, выделение, пустое состояние.
 * Добавляет [FlowConfig] — конфигурацию размещения (Vertical/Horizontal/Grid).
 *
 * Используется как содержимое [ScrollableBlock].
 *
 * ## Использование
 * ```kotlin
 * val list = FlowList<HBoxLayout>(FlowConfig.Vertical(rowHeight = 12, gap = 2))
 * list.setItems(rows)
 * val scrollable = ScrollableBlock(content = list, contentHeightProvider = { list.contentHeight })
 * ```
 */
class FlowList<T : BbfWidget>(
    var config: FlowConfig
) : BaseList<T>() {

    // ── Полная высота/ширина контента ─────────────────────────────────────

    override val contentHeight: Int get() = when (val c = config) {
        is FlowConfig.Vertical   -> _items.size * c.rowHeight + (_items.size - 1).coerceAtLeast(0) * c.gap
        is FlowConfig.Horizontal -> c.colHeight
        is FlowConfig.Grid       -> {
            val rows = (_items.size + c.columns - 1) / c.columns
            rows * c.itemHeight + (rows - 1).coerceAtLeast(0) * c.gap
        }
    }

    val contentWidth: Int get() = when (val c = config) {
        is FlowConfig.Vertical   -> 0
        is FlowConfig.Horizontal -> _items.size * c.colWidth + (_items.size - 1).coerceAtLeast(0) * c.gap
        is FlowConfig.Grid       -> c.columns * c.itemWidth + (c.columns - 1).coerceAtLeast(0) * c.gap
    }

    // ── Размещение элементов ──────────────────────────────────────────────

    override fun forEachItemCtx(ctx: RenderContext, block: (T, RenderContext) -> Unit) {
        when (val c = config) {
            is FlowConfig.Vertical -> {
                var y = 0
                _items.forEach { item ->
                    val itemCtx = ctx.child(offsetX = 0, offsetY = y, w = ctx.width, h = c.rowHeight)
                    block(item, itemCtx)
                    y += c.rowHeight + c.gap
                }
            }
            is FlowConfig.Horizontal -> {
                var x = 0
                _items.forEach { item ->
                    val itemCtx = ctx.child(offsetX = x, offsetY = 0, w = c.colWidth, h = ctx.height)
                    block(item, itemCtx)
                    x += c.colWidth + c.gap
                }
            }
            is FlowConfig.Grid -> {
                _items.forEachIndexed { index, item ->
                    val col = index % c.columns
                    val row = index / c.columns
                    val x = col * (c.itemWidth  + c.gap)
                    val y = row * (c.itemHeight + c.gap)
                    val itemCtx = ctx.child(offsetX = x, offsetY = y, w = c.itemWidth, h = c.itemHeight)
                    block(item, itemCtx)
                }
            }
        }
    }
}

// ── Конфигурация потока ───────────────────────────────────────────────────

/**
 * Конфигурация направления и размеров элементов в [FlowList].
 */
sealed class FlowConfig {

    /** Одна колонка, элементы идут вниз. Ширина элемента = ширина контекста. */
    data class Vertical(
        val rowHeight: Int,
        val gap: Int = 0
    ) : FlowConfig()

    /** Одна строка, элементы идут вправо. Высота элемента = высота контекста. */
    data class Horizontal(
        val colWidth: Int,
        val colHeight: Int,
        val gap: Int = 0
    ) : FlowConfig()

    /** Сетка: n колонок, строки идут вниз. */
    data class Grid(
        val columns: Int,
        val itemWidth: Int,
        val itemHeight: Int,
        val gap: Int = 0
    ) : FlowConfig()
}
