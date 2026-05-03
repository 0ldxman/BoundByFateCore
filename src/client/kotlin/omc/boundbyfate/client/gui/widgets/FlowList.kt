package omc.boundbyfate.client.gui.widgets

import omc.boundbyfate.client.gui.core.*

/**
 * Типизированный динамический список виджетов с настраиваемым направлением потока.
 *
 * В отличие от [VBoxLayout]/[HBoxLayout]/[GridLayout] (которые для статичной разметки),
 * [FlowList] предназначен для динамических данных: поддерживает add/remove в рантайме,
 * типизированный доступ к элементам, выделение и пустое состояние.
 *
 * Используется как содержимое [ScrollableBlock].
 *
 * ## Использование
 * ```kotlin
 * val list = FlowList<HBoxLayout>(FlowConfig.Vertical(rowHeight = 12, gap = 2))
 * list.setItems(savingThrows.map { buildSavingThrowRow(it) })
 *
 * val scrollable = ScrollableBlock(content = list, contentHeightProvider = { list.contentHeight })
 * ```
 */
class FlowList<T : BbfWidget>(
    var config: FlowConfig,
    var selectable: Boolean = false,
    var emptyWidget: BbfWidget? = null
) : BbfWidget() {

    private val _items = mutableListOf<T>()

    /** Типизированный доступ к элементам. */
    val items: List<T> get() = _items

    var selectedIndex: Int = -1
        private set

    var onSelected: ((T, Int) -> Unit)? = null

    // ── Полная высота/ширина контента (для ScrollableBlock) ───────────────

    val contentHeight: Int get() = when (val c = config) {
        is FlowConfig.Vertical   -> _items.size * c.rowHeight + (_items.size - 1).coerceAtLeast(0) * c.gap
        is FlowConfig.Horizontal -> c.colHeight
        is FlowConfig.Grid       -> {
            val rows = (_items.size + c.columns - 1) / c.columns
            rows * c.itemHeight + (rows - 1).coerceAtLeast(0) * c.gap
        }
    }

    val contentWidth: Int get() = when (val c = config) {
        is FlowConfig.Vertical   -> 0  // растягивается по контексту
        is FlowConfig.Horizontal -> _items.size * c.colWidth + (_items.size - 1).coerceAtLeast(0) * c.gap
        is FlowConfig.Grid       -> c.columns * c.itemWidth + (c.columns - 1).coerceAtLeast(0) * c.gap
    }

    // ── Управление элементами ─────────────────────────────────────────────

    fun add(item: T) {
        _items.add(item)
    }

    fun remove(item: T) {
        val idx = _items.indexOf(item)
        if (idx >= 0) {
            _items.removeAt(idx)
            if (selectedIndex == idx) selectedIndex = -1
            else if (selectedIndex > idx) selectedIndex--
        }
    }

    fun setItems(items: List<T>) {
        _items.clear()
        _items.addAll(items)
        selectedIndex = -1
    }

    fun clear() {
        _items.clear()
        selectedIndex = -1
    }

    // ── Tick ──────────────────────────────────────────────────────────────

    override fun tick(ctx: RenderContext) {
        if (_items.isEmpty()) {
            emptyWidget?.tick(ctx)
            return
        }

        forEachItemCtx(ctx) { item, itemCtx ->
            item.tick(itemCtx)
        }
        tickAll(ctx.delta)
    }

    // ── Render ────────────────────────────────────────────────────────────

    override fun render(ctx: RenderContext) {
        if (_items.isEmpty()) {
            emptyWidget?.render(ctx)
            return
        }

        forEachItemCtx(ctx) { item, itemCtx ->
            // Подсветка выбранного элемента
            if (selectable && selectedIndex == _items.indexOf(item)) {
                itemCtx.drawContext.fillRect(
                    itemCtx.x, itemCtx.y, itemCtx.width, itemCtx.height,
                    Theme.panel.backgroundLight
                )
            }
            item.render(itemCtx)
        }
    }

    // ── Клики ─────────────────────────────────────────────────────────────

    fun handleClick(mouseX: Int, mouseY: Int, button: Int): Boolean {
        // Пробрасываем клик в каждый элемент
        // Нужен последний известный ctx — храним его
        return false  // элементы сами обрабатывают через свои Clickable
    }

    // ── Вспомогательные ───────────────────────────────────────────────────

    /**
     * Итерирует элементы, вычисляя [RenderContext] для каждого.
     */
    private inline fun forEachItemCtx(ctx: RenderContext, block: (T, RenderContext) -> Unit) {
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
