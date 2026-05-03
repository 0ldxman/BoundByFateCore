package omc.boundbyfate.client.gui.widgets

import omc.boundbyfate.client.gui.core.*

/**
 * Абстрактный базовый класс для всех списков виджетов.
 *
 * Содержит общую логику управления элементами, выделения и пустого состояния.
 * Конкретные реализации определяют как именно размещаются и анимируются элементы.
 *
 * Реализации:
 *   [FlowList]      — статичные размеры, Vertical/Horizontal/Grid через FlowConfig
 *   [AccordionList] — вертикальный список с анимацией расширения по высоте при hover
 */
abstract class BaseList<T : BbfWidget> : BbfWidget() {

    protected val _items = mutableListOf<T>()

    /** Типизированный доступ к элементам. */
    val items: List<T> get() = _items

    /** Можно ли выбирать элементы кликом. */
    var selectable: Boolean = false

    var selectedIndex: Int = -1
        protected set

    var onSelected: ((T, Int) -> Unit)? = null

    /** Виджет который показывается когда список пуст. */
    var emptyWidget: BbfWidget? = null

    // ── Полная высота контента (для ScrollableBlock) ───────────────────────

    abstract val contentHeight: Int

    // ── Управление элементами ─────────────────────────────────────────────

    open fun add(item: T) {
        _items.add(item)
        onItemAdded(item, _items.size - 1)
    }

    open fun remove(item: T) {
        val idx = _items.indexOf(item)
        if (idx >= 0) {
            _items.removeAt(idx)
            onItemRemoved(idx)
            if (selectedIndex == idx) selectedIndex = -1
            else if (selectedIndex > idx) selectedIndex--
        }
    }

    open fun setItems(items: List<T>) {
        _items.clear()
        _items.addAll(items)
        selectedIndex = -1
        onItemsReset()
    }

    open fun clear() {
        _items.clear()
        selectedIndex = -1
        onItemsReset()
    }

    // ── Хуки для дочерних классов ─────────────────────────────────────────

    /** Вызывается при добавлении элемента. Переопредели для инициализации анимаций и т.д. */
    protected open fun onItemAdded(item: T, index: Int) {}

    /** Вызывается при удалении элемента по индексу. */
    protected open fun onItemRemoved(index: Int) {}

    /** Вызывается при полной замене списка. */
    protected open fun onItemsReset() {}

    // ── Tick / Render ─────────────────────────────────────────────────────

    override fun tick(ctx: RenderContext) {
        if (_items.isEmpty()) {
            emptyWidget?.tick(ctx)
            tickAll(ctx.delta)
            return
        }
        forEachItemCtx(ctx) { item, itemCtx -> item.tick(itemCtx) }
        tickAll(ctx.delta)
    }

    override fun render(ctx: RenderContext) {
        if (_items.isEmpty()) {
            emptyWidget?.render(ctx)
            return
        }
        forEachItemCtx(ctx) { item, itemCtx ->
            if (selectable && selectedIndex == _items.indexOf(item)) {
                itemCtx.drawContext.fillRect(
                    itemCtx.x, itemCtx.y, itemCtx.width, itemCtx.height,
                    Theme.panel.backgroundLight
                )
            }
            item.render(itemCtx)
        }
    }

    /**
     * Итерирует элементы, вычисляя [RenderContext] для каждого.
     * Реализуется в дочерних классах — именно здесь определяется логика размещения.
     */
    protected abstract fun forEachItemCtx(ctx: RenderContext, block: (T, RenderContext) -> Unit)
}
