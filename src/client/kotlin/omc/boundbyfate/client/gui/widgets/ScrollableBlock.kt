package omc.boundbyfate.client.gui.widgets

import omc.boundbyfate.client.gui.components.Hoverable
import omc.boundbyfate.client.gui.core.*

/**
 * Скроллируемый контейнер над любым [BbfWidget].
 *
 * Обрезает контент по своим границам через [withClip] и смещает
 * дочерний контекст на [-scrollOffset] по вертикали.
 *
 * [contentHeightProvider] — лямбда которая возвращает полную высоту контента.
 * Это позволяет контенту самому знать свою высоту (например [FlowList.contentHeight]).
 *
 * ## Использование
 * ```kotlin
 * val list = FlowList<HBoxLayout>(FlowConfig.Vertical(rowHeight = 12, gap = 2))
 * val scrollable = ScrollableBlock(
 *     content = list,
 *     contentHeightProvider = { list.contentHeight }
 * )
 * ```
 */
class ScrollableBlock(
    val content: BbfWidget,
    val contentHeightProvider: () -> Int,
    /** Скорость прокрутки в пикселях за единицу скролла. */
    var scrollSpeed: Float = 8f
) : BbfWidget() {

    private var scrollOffset: Float = 0f
    private val hover = Hoverable(playSoundOnEnter = false)

    // ── Анимация скролла ──────────────────────────────────────────────────

    private val smoothScroll = animFloat(0f, speed = 0.25f)

    // ── Tick ──────────────────────────────────────────────────────────────

    override fun tick(ctx: RenderContext) {
        hover.update(ctx)

        // Ограничиваем скролл
        val maxScroll = (contentHeightProvider() - ctx.height).coerceAtLeast(0).toFloat()
        scrollOffset = scrollOffset.coerceIn(0f, maxScroll)
        smoothScroll.target = scrollOffset

        // Тикаем контент со смещённым контекстом
        val contentCtx = contentContext(ctx)
        content.tick(contentCtx)

        tickAll(ctx.delta)
    }

    // ── Render ────────────────────────────────────────────────────────────

    override fun render(ctx: RenderContext) {
        // Обрезаем контент по границам блока
        ctx.drawContext.withClip(ctx.x, ctx.y, ctx.width, ctx.height) {
            content.render(contentContext(ctx))
        }

        // Скроллбар (если контент больше видимой области)
        val contentH = contentHeightProvider()
        if (contentH > ctx.height) {
            drawScrollbar(ctx, contentH)
        }
    }

    // ── Скролл ────────────────────────────────────────────────────────────

    /**
     * Обрабатывает скролл мышью. Вызывается из экрана.
     * @return true если событие обработано
     */
    fun handleScroll(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (!hover.isHovered) return false
        scrollOffset -= (amount * scrollSpeed).toFloat()
        return true
    }

    // ── Вспомогательные ───────────────────────────────────────────────────

    /** Контекст для контента со смещением на scrollOffset. */
    private fun contentContext(ctx: RenderContext): RenderContext {
        val offset = smoothScroll.current.toInt()
        return ctx.child(offsetX = 0, offsetY = -offset, w = ctx.width, h = contentHeightProvider())
    }

    /** Рисует тонкий скроллбар справа. */
    private fun drawScrollbar(ctx: RenderContext, contentH: Int) {
        val trackH = ctx.height
        val thumbH = (trackH * ctx.height.toFloat() / contentH).toInt().coerceAtLeast(8)
        val maxScroll = (contentH - ctx.height).toFloat()
        val thumbY = if (maxScroll > 0) {
            ctx.y + ((smoothScroll.current / maxScroll) * (trackH - thumbH)).toInt()
        } else ctx.y

        val barX = ctx.right - 2
        // Трек
        ctx.drawContext.fillRect(barX, ctx.y, 2, trackH, Theme.panel.background)
        // Ползунок
        ctx.drawContext.fillRect(barX, thumbY, 2, thumbH, Theme.panel.border)
    }
}
