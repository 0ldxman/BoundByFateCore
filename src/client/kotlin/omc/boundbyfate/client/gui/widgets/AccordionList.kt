package omc.boundbyfate.client.gui.widgets

import omc.boundbyfate.client.gui.components.Hoverable
import omc.boundbyfate.client.gui.core.*

/**
 * Вертикальный список с accordion-анимацией.
 *
 * При наведении на элемент его высота плавно увеличивается до [expandedRowH],
 * а соседние элементы расходятся — верхние вверх, нижние вниз.
 *
 * Наследует [BaseList] — управление элементами, выделение, пустое состояние.
 *
 * ## Использование
 * ```kotlin
 * val list = AccordionList<HBoxLayout>(baseRowH = 11, expandedRowH = 16, gap = 1)
 * list.setItems(items)
 * val scrollable = ScrollableBlock(content = list, contentHeightProvider = { list.contentHeight })
 * ```
 */
class AccordionList<T : BbfWidget>(
    val baseRowH: Int,
    val expandedRowH: Int,
    val gap: Int = 0
) : BaseList<T>() {

    // ── Анимации высот — по одной на каждый элемент ───────────────────────

    private val heightAnims = mutableListOf<AnimState<Float>>()
    private val hovers      = mutableListOf<Hoverable>()

    // ── Полная высота контента ────────────────────────────────────────────

    override val contentHeight: Int get() {
        if (_items.isEmpty()) return 0
        val totalH = heightAnims.sumOf { it.current.toInt() }
        val totalGap = (_items.size - 1).coerceAtLeast(0) * gap
        return totalH + totalGap
    }

    // ── Хуки BaseList ─────────────────────────────────────────────────────

    override fun onItemAdded(item: T, index: Int) {
        heightAnims.add(animFloat(baseRowH.toFloat(), speed = 0.2f))
        hovers.add(Hoverable(playSoundOnEnter = false))
    }

    override fun onItemRemoved(index: Int) {
        if (index < heightAnims.size) heightAnims.removeAt(index)
        if (index < hovers.size)      hovers.removeAt(index)
    }

    override fun onItemsReset() {
        heightAnims.clear()
        hovers.clear()
        _items.forEach { _ ->
            heightAnims.add(animFloat(baseRowH.toFloat(), speed = 0.2f))
            hovers.add(Hoverable(playSoundOnEnter = false))
        }
    }

    // ── Размещение элементов ──────────────────────────────────────────────

    override fun forEachItemCtx(ctx: RenderContext, block: (T, RenderContext) -> Unit) {
        // Обновляем hover и целевые высоты
        var y = 0
        _items.forEachIndexed { i, _ ->
            val h = heightAnims[i].current.toInt()
            hovers[i].update(ctx.mouseX, ctx.mouseY, ctx.x, ctx.y + y, ctx.width, h)
            heightAnims[i].target = if (hovers[i].isHovered) expandedRowH.toFloat() else baseRowH.toFloat()
            heightAnims[i].tick(ctx.delta)
            y += h + gap
        }

        // Рендерим с актуальными высотами.
        // Половина прироста высоты уходит вверх (смещение вверх на expandDelta/2),
        // половина вниз — это создаёт симметричное расталкивание.
        var renderY = 0
        _items.forEachIndexed { i, item ->
            val h = heightAnims[i].current.toInt()
            val expandDelta = h - baseRowH  // 0 если не расширен
            val offsetY = renderY - expandDelta / 2
            val itemCtx = ctx.child(offsetX = 0, offsetY = offsetY, w = ctx.width, h = h)
            block(item, itemCtx)
            renderY += h + gap
        }
    }
}
