package omc.boundbyfate.client.gui.core

/**
 * Система Layout для автоматического размещения виджетов.
 *
 * Упрощенная система без сложных constraints — виджеты имеют фиксированные размеры,
 * контейнеры автоматически размещают их по правилам.
 *
 * ## Использование
 *
 * ```kotlin
 * val layout = vbox(gap = 8, padding = 10) {
 *     add(TitleWidget("Character"), height = 30)
 *     add(StatsPanel(), height = 200)
 *     add(ButtonRow(), height = 40)
 * }
 *
 * // В render():
 * layout.render(ctx)
 * ```
 */

// ── Layout Item ───────────────────────────────────────────────────────────

/**
 * Элемент layout — виджет с размерами и позицией.
 */
data class LayoutItem(
    val widget: BbfWidget,
    var x: Int = 0,
    var y: Int = 0,
    var width: Int = 0,
    var height: Int = 0
)

// ── Layout Containers ─────────────────────────────────────────────────────

/**
 * Вертикальный контейнер — размещает детей сверху вниз.
 *
 * ## Использование
 *
 * ```kotlin
 * val container = VBoxLayout(gap = 8, padding = 10).apply {
 *     add(titleWidget, height = 30)
 *     add(statsPanel, height = 200)
 *     add(buttonRow, height = 40)
 * }
 *
 * // В tick/render:
 * container.tick(ctx)
 * container.render(ctx)
 * ```
 */
class VBoxLayout(
    var gap: Int = 0,
    var padding: Int = 0,
    var horizontalAlign: Align = Align.START
) : BbfWidget() {

    enum class Align { START, CENTER, END, FILL }

    private val items = mutableListOf<LayoutItem>()
    private var needsLayout = true

    /** Общая высота контейнера. */
    var totalHeight = 0
        private set

    /** Максимальная ширина контейнера. */
    var totalWidth = 0
        private set

    /**
     * Добавляет виджет в контейнер.
     *
     * @param widget виджет для добавления
     * @param height высота виджета
     * @param width ширина виджета (если null — заполняет доступное пространство)
     */
    fun add(widget: BbfWidget, height: Int, width: Int? = null) {
        items += LayoutItem(widget, width = width ?: 0, height = height)
        needsLayout = true
    }

    fun clear() {
        items.clear()
        needsLayout = true
    }

    /**
     * Пересчитывает позиции виджетов.
     * Вызывается автоматически при изменениях или вручную если нужно обновить layout.
     */
    fun layout(containerWidth: Int) {
        var y = padding
        var maxW = 0

        items.forEach { item ->
            // Ширина виджета
            val itemWidth = if (item.width > 0) item.width else containerWidth - padding * 2

            // Позиция по X в зависимости от выравнивания
            val x = when (horizontalAlign) {
                Align.START -> padding
                Align.CENTER -> padding + (containerWidth - padding * 2 - itemWidth) / 2
                Align.END -> containerWidth - padding - itemWidth
                Align.FILL -> padding
            }

            item.x = x
            item.y = y
            item.width = itemWidth

            y += item.height + gap
            maxW = maxW.coerceAtLeast(itemWidth)
        }

        totalHeight = y - gap + padding
        totalWidth = maxW + padding * 2
        needsLayout = false
    }

    override fun tick(ctx: RenderContext) {
        if (needsLayout) layout(ctx.width)

        items.forEach { item ->
            val childCtx = ctx.child(item.x, item.y, item.width, item.height)
            item.widget.tick(childCtx)
        }
        tickAll(ctx.delta)
    }

    override fun render(ctx: RenderContext) {
        items.forEach { item ->
            val childCtx = ctx.child(item.x, item.y, item.width, item.height)
            item.widget.render(childCtx)
        }
    }
}

/**
 * Горизонтальный контейнер — размещает детей слева направо.
 */
class HBoxLayout(
    var gap: Int = 0,
    var padding: Int = 0,
    var verticalAlign: Align = Align.START
) : BbfWidget() {

    enum class Align { START, CENTER, END, FILL }

    private val items = mutableListOf<LayoutItem>()
    private var needsLayout = true

    var totalWidth = 0
        private set

    var totalHeight = 0
        private set

    /**
     * Добавляет виджет в контейнер.
     *
     * @param widget виджет для добавления
     * @param width ширина виджета
     * @param height высота виджета (если null — заполняет доступное пространство)
     */
    fun add(widget: BbfWidget, width: Int, height: Int? = null) {
        items += LayoutItem(widget, width = width, height = height ?: 0)
        needsLayout = true
    }

    fun clear() {
        items.clear()
        needsLayout = true
    }

    fun layout(containerHeight: Int) {
        var x = padding
        var maxH = 0

        items.forEach { item ->
            val itemHeight = if (item.height > 0) item.height else containerHeight - padding * 2

            val y = when (verticalAlign) {
                Align.START -> padding
                Align.CENTER -> padding + (containerHeight - padding * 2 - itemHeight) / 2
                Align.END -> containerHeight - padding - itemHeight
                Align.FILL -> padding
            }

            item.x = x
            item.y = y
            item.height = itemHeight

            x += item.width + gap
            maxH = maxH.coerceAtLeast(itemHeight)
        }

        totalWidth = x - gap + padding
        totalHeight = maxH + padding * 2
        needsLayout = false
    }

    override fun tick(ctx: RenderContext) {
        if (needsLayout) layout(ctx.height)

        items.forEach { item ->
            val childCtx = ctx.child(item.x, item.y, item.width, item.height)
            item.widget.tick(childCtx)
        }
        tickAll(ctx.delta)
    }

    override fun render(ctx: RenderContext) {
        items.forEach { item ->
            val childCtx = ctx.child(item.x, item.y, item.width, item.height)
            item.widget.render(childCtx)
        }
    }
}

/**
 * Grid контейнер — размещает детей в сетке.
 *
 * ## Использование
 *
 * ```kotlin
 * val grid = GridLayout(columns = 3, gap = 8, padding = 10).apply {
 *     add(icon1, 48, 48)
 *     add(icon2, 48, 48)
 *     add(icon3, 48, 48)
 *     add(icon4, 48, 48)
 *     // Автоматически размещает в 2 ряда по 3 колонки
 * }
 * ```
 */
class GridLayout(
    var columns: Int,
    var gap: Int = 0,
    var padding: Int = 0
) : BbfWidget() {

    private val items = mutableListOf<LayoutItem>()
    private var needsLayout = true

    var totalWidth = 0
        private set

    var totalHeight = 0
        private set

    fun add(widget: BbfWidget, width: Int, height: Int) {
        items += LayoutItem(widget, width = width, height = height)
        needsLayout = true
    }

    fun clear() {
        items.clear()
        needsLayout = true
    }

    fun layout() {
        if (items.isEmpty()) {
            totalWidth = padding * 2
            totalHeight = padding * 2
            return
        }

        val rows = (items.size + columns - 1) / columns
        var maxW = 0
        var maxH = 0

        items.forEachIndexed { index, item ->
            val col = index % columns
            val row = index / columns

            item.x = padding + col * (item.width + gap)
            item.y = padding + row * (item.height + gap)

            maxW = maxW.coerceAtLeast(item.x + item.width)
            maxH = maxH.coerceAtLeast(item.y + item.height)
        }

        totalWidth = maxW + padding
        totalHeight = maxH + padding
        needsLayout = false
    }

    override fun tick(ctx: RenderContext) {
        if (needsLayout) layout()

        items.forEach { item ->
            val childCtx = ctx.child(item.x, item.y, item.width, item.height)
            item.widget.tick(childCtx)
        }
        tickAll(ctx.delta)
    }

    override fun render(ctx: RenderContext) {
        items.forEach { item ->
            val childCtx = ctx.child(item.x, item.y, item.width, item.height)
            item.widget.render(childCtx)
        }
    }
}

/**
 * Абсолютный контейнер — размещает детей по абсолютным координатам.
 * Полезно для сложных кастомных layout.
 */
class AbsoluteLayout : BbfWidget() {

    private val items = mutableListOf<LayoutItem>()

    fun add(widget: BbfWidget, x: Int, y: Int, width: Int, height: Int) {
        items += LayoutItem(widget, x, y, width, height)
    }

    fun clear() = items.clear()

    override fun tick(ctx: RenderContext) {
        items.forEach { item ->
            val childCtx = ctx.child(item.x, item.y, item.width, item.height)
            item.widget.tick(childCtx)
        }
        tickAll(ctx.delta)
    }

    override fun render(ctx: RenderContext) {
        items.forEach { item ->
            val childCtx = ctx.child(item.x, item.y, item.width, item.height)
            item.widget.render(childCtx)
        }
    }
}

// ── DSL хелперы ───────────────────────────────────────────────────────────

/** Создает VBox с DSL. */
inline fun vbox(
    gap: Int = 0,
    padding: Int = 0,
    align: VBoxLayout.Align = VBoxLayout.Align.START,
    block: VBoxLayout.() -> Unit
) = VBoxLayout(gap, padding, align).apply(block)

/** Создает HBox с DSL. */
inline fun hbox(
    gap: Int = 0,
    padding: Int = 0,
    align: HBoxLayout.Align = HBoxLayout.Align.START,
    block: HBoxLayout.() -> Unit
) = HBoxLayout(gap, padding, align).apply(block)

/** Создает Grid с DSL. */
inline fun grid(
    columns: Int,
    gap: Int = 0,
    padding: Int = 0,
    block: GridLayout.() -> Unit
) = GridLayout(columns, gap, padding).apply(block)
