package omc.boundbyfate.client.gui.widgets

import net.minecraft.text.Text
import omc.boundbyfate.client.gui.components.Clickable
import omc.boundbyfate.client.gui.components.Hoverable
import omc.boundbyfate.client.gui.core.*

/**
 * Универсальная кнопка.
 *
 * ## Использование
 *
 * ```kotlin
 * val button = BbfButton(
 *     text = "Выбрать расу",
 *     width = 120,
 *     height = 24
 * )
 * button.onClick = { println("Clicked!") }
 * ```
 */
class BbfButton(
    var text: String,
    var width: Int = 96,
    var height: Int = 20,
    var enabled: Boolean = true
) : BbfWidget() {

    private val hover = Hoverable()
    private val click = Clickable(enabled = { enabled })
    
    private val scale = animFloat(1f, speed = 0.15f)
    private val bgColor = animColor(Theme.button.normal, speed = 0.15f)

    /** Колбек на клик. */
    var onClick: (() -> Unit)? = null

    init {
        click.onClick { onClick?.invoke() }
    }

    override fun tick(ctx: RenderContext) {
        hover.update(ctx)
        
        scale.target = when {
            !enabled -> 1f
            hover.isHovered -> 1.05f
            else -> 1f
        }

        bgColor.target = when {
            !enabled -> Theme.button.disabled
            hover.isHovered -> Theme.button.hovered
            else -> Theme.button.normal
        }

        tickAll(ctx.delta)
    }

    override fun render(ctx: RenderContext) {
        ctx.drawContext.transform(
            pivotX = ctx.cx.toFloat(),
            pivotY = ctx.cy.toFloat(),
            scale = scale.current
        ) {
            // Фон
            fillRectWithBorder(ctx.x, ctx.y, width, height,
                bgColor.current, Theme.panel.border)

            // Текст
            val textColor = if (enabled) Theme.button.text else Theme.button.textDisabled
            drawScaledText(text, ctx.cx, ctx.cy - 4,
                scale = 0.8f, color = textColor, align = TextAlign.CENTER)
        }
    }

    fun handleClick(mouseX: Int, mouseY: Int, button: Int): Boolean =
        click.handle(mouseX, mouseY, button, hover.isHovered)
}
