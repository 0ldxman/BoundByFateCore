package omc.boundbyfate.client.gui.widgets

import omc.boundbyfate.client.gui.components.Hoverable
import omc.boundbyfate.client.gui.core.*

/**
 * Универсальная полоска прогресса.
 *
 * Используется для опыта, здоровья, маны и т.д.
 *
 * ## Использование
 *
 * ```kotlin
 * val expBar = BarWidget(
 *     current = 750,
 *     max = 1000,
 *     width = 200,
 *     height = 8,
 *     fillColor = 0xFF55FF55.toInt()
 * )
 * ```
 */
class BarWidget(
    var current: Float = 0f,
    var max: Float = 100f,
    var width: Int = 200,
    var height: Int = 8,
    var fillColor: Int = Theme.health.full,
    var backgroundColor: Int = Theme.health.background,
    var borderColor: Int = Theme.health.border,
    var showText: Boolean = false,
    var textFormatter: ((Float, Float) -> String)? = null
) : BbfWidget() {

    private val hover = Hoverable(playSoundOnEnter = false)
    private val animatedProgress = animFloat(0f, speed = 0.1f)
    private val tooltip = AnimatedTooltip {
        val text = textFormatter?.invoke(current, max) ?: "${current.toInt()}/${max.toInt()}"
        net.minecraft.text.Text.literal(text)
    }

    /** Прогресс (0..1). */
    val progress get() = (current / max).coerceIn(0f, 1f)

    override fun tick(ctx: RenderContext) {
        hover.update(ctx)
        animatedProgress.target = progress
        tooltip.update(hover.isHovered, ctx.delta)
        tickAll(ctx.delta)
    }

    override fun render(ctx: RenderContext) {
        // Фон
        ctx.drawContext.fillRectWithBorder(ctx.x, ctx.y, width, height,
            backgroundColor, borderColor)

        // Заливка
        val fillWidth = (width * animatedProgress.current).toInt().coerceAtLeast(0)
        if (fillWidth > 0) {
            ctx.drawContext.fillRect(ctx.x + 1, ctx.y + 1, fillWidth - 2, height - 2, fillColor)
        }

        // Текст (опционально)
        if (showText) {
            val text = textFormatter?.invoke(current, max) ?: "${current.toInt()}/${max.toInt()}"
            ctx.drawContext.drawScaledText(text, ctx.cx, ctx.y + height / 2 - 4,
                scale = 0.7f, color = Theme.text.primary, align = TextAlign.CENTER, shadow = true)
        }

        // Тултип
        tooltip.render(ctx)
    }
}
