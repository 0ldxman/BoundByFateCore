package omc.boundbyfate.client.gui.widgets

import omc.boundbyfate.client.gui.components.Clickable
import omc.boundbyfate.client.gui.components.Focusable
import omc.boundbyfate.client.gui.components.FocusManager
import omc.boundbyfate.client.gui.components.Hoverable
import omc.boundbyfate.client.gui.core.*
import org.lwjgl.glfw.GLFW

/**
 * Универсальное текстовое поле.
 *
 * ## Использование
 *
 * ```kotlin
 * val nameField = BbfTextField(
 *     placeholder = "Введите имя персонажа",
 *     width = 200,
 *     height = 24
 * )
 * nameField.onTextChanged = { newText -> println("Name: $newText") }
 * ```
 */
class BbfTextField(
    var text: String = "",
    var placeholder: String = "",
    var width: Int = 200,
    var height: Int = 24,
    var maxLength: Int = 32
) : BbfWidget() {

    private val hover = Hoverable(playSoundOnEnter = false)
    private val click = Clickable()
    private val focus = Focusable()

    private val borderColor = animColor(Theme.input.border, speed = 0.15f)
    private val bgColor = animColor(Theme.input.background, speed = 0.15f)
    private var cursorBlink = 0f

    /** Колбек на изменение текста. */
    var onTextChanged: ((String) -> Unit)? = null

    init {
        click.onClick { FocusManager.requestFocus(focus) }

        focus.onKeyPress { key, _ ->
            when (key) {
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (text.isNotEmpty()) {
                        text = text.dropLast(1)
                        onTextChanged?.invoke(text)
                    }
                    true
                }
                GLFW.GLFW_KEY_ESCAPE -> {
                    FocusManager.clearFocus()
                    true
                }
                else -> false
            }
        }

        focus.onCharTyped { char ->
            if (text.length < maxLength && char.isLetterOrDigit() || char == ' ' || char == '_' || char == '-') {
                text += char
                onTextChanged?.invoke(text)
                true
            } else false
        }
    }

    override fun tick(ctx: RenderContext) {
        hover.update(ctx)

        borderColor.target = if (focus.isFocused) Theme.input.borderFocused else Theme.input.border
        bgColor.target = if (focus.isFocused) Theme.input.backgroundFocused else Theme.input.background

        if (focus.isFocused) {
            cursorBlink += ctx.delta * 2f
        }

        tickAll(ctx.delta)
    }

    override fun render(ctx: RenderContext) {
        // Фон
        ctx.drawContext.fillRectWithBorder(ctx.x, ctx.y, width, height,
            bgColor.current, borderColor.current)

        // Текст или placeholder
        val displayText = text.ifEmpty { placeholder }
        val textColor = if (text.isEmpty()) Theme.input.placeholder else Theme.input.text

        ctx.drawContext.withClip(ctx.x + 4, ctx.y, width - 8, height) {
            drawScaledText(displayText, ctx.x + 6, ctx.y + height / 2 - 4,
                scale = 0.8f, color = textColor)

            // Курсор
            if (focus.isFocused && (cursorBlink % 1f) < 0.5f) {
                val tr = net.minecraft.client.MinecraftClient.getInstance().textRenderer
                val cursorX = ctx.x + 6 + (tr.getWidth(text) * 0.8f).toInt()
                fillRect(cursorX, ctx.y + 4, 1, height - 8, Theme.input.cursor)
            }
        }
    }

    fun handleClick(mouseX: Int, mouseY: Int, button: Int): Boolean =
        click.handle(mouseX, mouseY, button, hover.isHovered)
}
