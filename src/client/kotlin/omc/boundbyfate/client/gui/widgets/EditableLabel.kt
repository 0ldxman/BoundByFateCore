package omc.boundbyfate.client.gui.widgets

import net.minecraft.client.MinecraftClient
import org.lwjgl.glfw.GLFW
import omc.boundbyfate.client.gui.components.Clickable
import omc.boundbyfate.client.gui.components.Focusable
import omc.boundbyfate.client.gui.components.FocusManager
import omc.boundbyfate.client.gui.components.Hoverable
import omc.boundbyfate.client.gui.core.*

/**
 * Редактируемый текстовый лейбл.
 *
 * Состояния:
 *   IDLE     — текст + иконка пера, divider скрыт
 *   HOVERED  — divider плавно появляется от центра к краям
 *   EDITING  — divider акцентного цвета, иконка ✓, активен ввод текста
 *
 * Анимация divider:
 *   [dividerAlpha] — прозрачность (0→1 при появлении)
 *   [dividerFade]  — fadeRatio (0.5→0 при появлении = "раскрытие от центра")
 *
 * ## Использование
 * ```kotlin
 * val nameLabel = EditableLabel("Ричард Зорге")
 * nameLabel.onConfirm = { newName -> character.name = newName }
 * // В экране пробрасываем события:
 * nameLabel.handleClick(mouseX, mouseY, button)
 * nameLabel.handleChar(char)
 * nameLabel.handleKey(key, mods)
 * ```
 */
class EditableLabel(
    var text: String,
    var textColor: Int = -1,          // -1 = Theme.text.primary
    var textScale: Float = 1f,
    var accentColor: Int = -1,        // -1 = Theme.text.accent
    /** Ширина иконки пера/галочки в пикселях. */
    var iconWidth: Int = 8,
    /** Зазор между текстом и иконкой. */
    var iconGap: Int = 3,
    /** Колбек при подтверждении изменения. */
    var onConfirm: ((String) -> Unit)? = null
) : BbfWidget() {

    // ── Состояния ─────────────────────────────────────────────────────────

    private enum class State { IDLE, HOVERED, EDITING }
    private var state = State.IDLE

    // Буфер редактирования — активен только в EDITING
    private var editBuffer = ""
    private var cursorTimer = 0f
    private var cursorVisible = false

    // ── Поведение ─────────────────────────────────────────────────────────

    private val hover  = Hoverable()
    private val click  = Clickable()
    private val focus  = Focusable()

    // ── Анимации divider ──────────────────────────────────────────────────

    /** Прозрачность divider (0 = скрыт, 1 = виден). */
    private val dividerAlpha = animFloat(0f, speed = 0.15f)

    /**
     * fadeRatio divider (0.5 = только центральный пиксель, 0 = полная линия).
     * При появлении: 0.5 → 0 (раскрытие от центра к краям).
     * При исчезновении: 0 → 0.5 (схлопывание к центру).
     */
    private val dividerFade = animFloat(0.5f, speed = 0.12f)

    /** Цвет divider — интерполируется между border и accent. */
    private val dividerColor = animColor(Theme.panel.border, speed = 0.15f)

    init {
        click.onClick { enterEditing() }

        focus.onFocus {
            cursorVisible = true
            cursorTimer = 0f
        }
        focus.onBlur {
            if (state == State.EDITING) cancelEditing()
        }
        focus.onKeyPress { key, _ ->
            when (key) {
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { confirmEditing(); true }
                GLFW.GLFW_KEY_ESCAPE -> { cancelEditing(); true }
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (editBuffer.isNotEmpty()) editBuffer = editBuffer.dropLast(1)
                    true
                }
                else -> false
            }
        }
        focus.onCharTyped { char ->
            if (state == State.EDITING) { editBuffer += char; true } else false
        }
    }

    // ── Tick ──────────────────────────────────────────────────────────────

    override fun tick(ctx: RenderContext) {
        hover.update(ctx)

        // Переходы состояний
        when (state) {
            State.IDLE -> {
                if (hover.isHovered) {
                    state = State.HOVERED
                    dividerAlpha.target = 1f
                    dividerFade.target  = 0f
                    dividerColor.target = Theme.panel.border
                }
            }
            State.HOVERED -> {
                if (!hover.isHovered && state != State.EDITING) {
                    state = State.IDLE
                    dividerAlpha.target = 0f
                    dividerFade.target  = 0.5f
                }
            }
            State.EDITING -> {
                // Мигание курсора
                cursorTimer += ctx.delta
                if (cursorTimer >= 0.5f) {
                    cursorTimer = 0f
                    cursorVisible = !cursorVisible
                }
            }
        }

        tickAll(ctx.delta)
    }

    // ── Render ────────────────────────────────────────────────────────────

    override fun render(ctx: RenderContext) {
        val resolvedText  = if (color == -1) Theme.text.primary else color
        val resolvedAccent = if (accentColor == -1) Theme.text.accent else accentColor

        // Зоны
        val iconX  = ctx.right - iconWidth
        val textW  = ctx.width - iconWidth - iconGap
        val textY  = ctx.cy - ((8f * textScale) / 2f).toInt()
        val lineY  = ctx.bottom - 1  // divider под текстом

        // ── Текст ─────────────────────────────────────────────────────────
        ctx.drawContext.withClip(ctx.x, ctx.y, textW, ctx.height) {
            val displayText = when (state) {
                State.EDITING -> editBuffer + if (cursorVisible) "|" else ""
                else          -> text
            }
            drawScaledText(
                displayText, ctx.x, textY,
                scale = textScale,
                color = if (state == State.EDITING) resolvedAccent else resolvedText,
                shadow = false
            )
        }

        // ── Иконка ────────────────────────────────────────────────────────
        val icon = if (state == State.EDITING) "✓" else "✎"
        val iconColor = if (state == State.EDITING) resolvedAccent else Theme.text.disabled
        ctx.drawContext.drawScaledText(
            icon, iconX + iconWidth / 2, textY,
            scale = textScale * 0.85f,
            color = iconColor,
            align = TextAlign.CENTER,
            shadow = false
        )

        // ── Divider ───────────────────────────────────────────────────────
        if (dividerAlpha.current > 0.01f) {
            val lineColor = dividerColor.current.withAlpha(dividerAlpha.current)
            val lineW = ctx.width
            val clampedFade = dividerFade.current.coerceIn(0f, 0.5f)

            for (i in 0 until lineW) {
                val t = i.toFloat() / lineW
                val edgeDist = minOf(t, 1f - t)
                val pixelAlpha = if (edgeDist < clampedFade) edgeDist / clampedFade else 1f
                val pixelColor = lineColor.withAlpha(dividerAlpha.current * pixelAlpha)
                ctx.drawContext.fillRect(ctx.x + i, lineY, 1, 1, pixelColor)
            }
        }
    }

    // ── Управление состоянием ─────────────────────────────────────────────

    private fun enterEditing() {
        if (state == State.EDITING) return
        state = State.EDITING
        editBuffer = text
        cursorTimer = 0f
        cursorVisible = true
        FocusManager.requestFocus(focus)
        val resolvedAccent = if (accentColor == -1) Theme.text.accent else accentColor
        dividerColor.target = resolvedAccent
        dividerAlpha.target = 1f
        dividerFade.target  = 0f
    }

    private fun confirmEditing() {
        if (state != State.EDITING) return
        text = editBuffer
        onConfirm?.invoke(text)
        exitEditing()
    }

    private fun cancelEditing() {
        if (state != State.EDITING) return
        exitEditing()
    }

    private fun exitEditing() {
        state = State.IDLE
        FocusManager.clearFocus()
        dividerAlpha.target = 0f
        dividerFade.target  = 0.5f
        dividerColor.target = Theme.panel.border
        cursorVisible = false
    }

    // ── Проброс событий из экрана ─────────────────────────────────────────

    fun handleClick(mouseX: Int, mouseY: Int, button: Int): Boolean =
        click.handle(mouseX, mouseY, button, hover.isHovered)

    fun handleChar(char: Char): Boolean =
        focus.handleCharTyped(char)

    fun handleKey(key: Int, mods: Int): Boolean =
        focus.handleKeyPress(key, mods)

    // ── Вспомогательные ───────────────────────────────────────────────────

    /** Цвет текста — хранится отдельно чтобы не конфликтовать с полем [color] в BbfWidget. */
    private val color get() = textColor
}
