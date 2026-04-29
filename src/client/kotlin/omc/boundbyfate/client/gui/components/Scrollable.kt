package omc.boundbyfate.client.gui.components

import net.minecraft.util.math.MathHelper
import omc.boundbyfate.client.gui.core.UiSounds
import omc.boundbyfate.client.gui.core.playUi

/**
 * Компонент прокрутки.
 *
 * ## Использование
 *
 * ```kotlin
 * class ScrollList : AnimOwner() {
 *     val scroll = Scrollable()
 *
 *     fun tick(ctx: RenderContext) {
 *         scroll.contentHeight = items.size * ITEM_H
 *         scroll.viewHeight = ctx.height
 *     }
 *
 *     fun render(ctx: RenderContext) {
 *         ctx.drawContext.withClip(ctx.x, ctx.y, ctx.width, ctx.height) {
 *             items.forEachIndexed { i, item ->
 *                 val itemY = ctx.y + i * ITEM_H - scroll.offset.toInt()
 *                 item.render(ctx.child(0, itemY - ctx.y, ctx.width, ITEM_H))
 *             }
 *         }
 *         scroll.renderScrollbar(ctx.drawContext, ctx.x + ctx.width - 4, ctx.y, 4, ctx.height)
 *     }
 *
 *     fun handleScroll(amount: Double): Boolean = scroll.handleScroll(amount)
 * }
 * ```
 */
class Scrollable(
    var scrollSpeed: Float = 20f,
    var playSoundOnScroll: Boolean = false
) {
    var contentHeight = 0
    var viewHeight = 0

    /** Текущий offset прокрутки (анимированный). */
    var offset = 0f
        private set

    private var targetOffset = 0f

    val maxOffset get() = (contentHeight - viewHeight).coerceAtLeast(0).toFloat()

    /** Прогресс прокрутки (0..1). */
    val progress get() = if (maxOffset > 0f) offset / maxOffset else 0f

    fun handleScroll(amount: Double): Boolean {
        if (contentHeight <= viewHeight) return false
        targetOffset = (targetOffset - amount.toFloat() * scrollSpeed).coerceIn(0f, maxOffset)
        if (playSoundOnScroll) UiSounds.current.scroll?.playUi(volume = 0.3f)
        return true
    }

    fun tick(delta: Float) {
        offset = MathHelper.lerp(0.2f, offset, targetOffset)
    }

    fun scrollTo(position: Float) {
        targetOffset = position.coerceIn(0f, maxOffset)
    }

    fun scrollToTop() = scrollTo(0f)
    fun scrollToBottom() = scrollTo(maxOffset)

    /**
     * Рисует скроллбар.
     */
    fun renderScrollbar(
        ctx: net.minecraft.client.gui.DrawContext,
        x: Int, y: Int, w: Int, h: Int,
        trackColor: Int = 0x33FFFFFF.toInt(),
        thumbColor: Int = 0x88FFFFFF.toInt()
    ) {
        if (maxOffset <= 0f) return

        // Трек
        ctx.fill(x, y, x + w, y + h, trackColor)

        // Ползунок
        val thumbH = ((viewHeight.toFloat() / contentHeight) * h).toInt().coerceAtLeast(20)
        val thumbY = y + ((progress) * (h - thumbH)).toInt()
        ctx.fill(x, thumbY, x + w, thumbY + thumbH, thumbColor)
    }
}
