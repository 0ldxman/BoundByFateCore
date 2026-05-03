package omc.boundbyfate.client.gui.widgets

import net.minecraft.client.MinecraftClient
import org.lwjgl.glfw.GLFW
import omc.boundbyfate.client.gui.components.Clickable
import omc.boundbyfate.client.gui.components.Focusable
import omc.boundbyfate.client.gui.components.FocusManager
import omc.boundbyfate.client.gui.components.Hoverable
import omc.boundbyfate.client.gui.core.*

/**
 * Редактируемый текстовый лейбл без фона.
 *
 * Состояния:
 *   IDLE     — текст + иконка пера, divider скрыт
 *   HOVERED  — divider раскрывается от центра к краям (белый/border)
 *   EDITING  — divider акцентного цвета, иконка ✓, активен ввод текста
 *
 * Анимация divider через [revealProgress] (0 = схлопнут к центру, 1 = полностью раскрыт).
 * Цвет divider меняется через [dividerColor].
 *
 * Иконка ✎/✓ рисуется сразу после текста (не у правого края).
 *
 * ## Использование
 * ```kotlin
 * val nameLabel = EditableLabel("Имя персонажа", align = TextAlign.CENTER)
 * nameLabel.onConfirm = { newName -> character.name = newName }
 * ```
 */
class EditableLabel(
    var text: String,
    var textColor: Int = -1,       // -1 = Theme.text.primary
    var textScale: Float = 1f,
    var accentColor: Int = -1,     // -1 = Theme.text.accent
    var align: TextAlign = TextAlign.LEFT,
    var iconGap: Int = 3,
    var onConfirm: ((String) -> Unit)? = null
) : BbfWidget() {

    // ── Состояния ─────────────────────────────────────────────────────────

    private enum class State { IDLE, HOVERED, EDITING }
    private var state = State.IDLE

    private var editBuffer = ""
    private var cursorTimer = 0f
    private var cursorVisible = false

    // ── Поведение ─────────────────────────────────────────────────────────

    private val hover = Hoverable()
    private val click = Clickable()
    private val focus = Focusable()

    // ── Анимации divider ──────────────────────────────────────────────────

    /**
     * Прогресс раскрытия divider (0 = схлопнут к центру, 1 = полностью раскрыт).
     */
    private val revealProgress = animFloat(0f, speed = 0.18f)

    /**
     * Прогресс окрашивания в акцентный цвет (0 = весь белый, 1 = весь акцентный).
     * Анимация идентична [revealProgress] — цвет "раскрывается" от центра к краям.
     * При выходе из editing — схлопывается обратно к центру.
     */
    private val accentProgress = animFloat(0f, speed = 0.18f)

    init {
        click.onClick { enterEditing() }

        focus.onFocus { /* не сбрасываем таймер здесь */ }
        focus.onBlur  { if (state == State.EDITING) cancelEditing() }

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

        when (state) {
            State.IDLE -> {
                if (hover.isHovered) {
                    state = State.HOVERED
                    revealProgress.target = 1f
                }
            }
            State.HOVERED -> {
                if (!hover.isHovered) {
                    state = State.IDLE
                    revealProgress.target = 0f
                }
            }
            State.EDITING -> {
                // Мигание курсора — только инкремент, без сброса при фокусе
                cursorTimer += ctx.delta
                if (cursorTimer >= 0.53f) {
                    cursorTimer -= 0.53f
                    cursorVisible = !cursorVisible
                }
            }
        }

        tickAll(ctx.delta)
    }

    // ── Render ────────────────────────────────────────────────────────────

    override fun render(ctx: RenderContext) {
        val tr = MinecraftClient.getInstance().textRenderer
        val resolvedText   = if (textColor == -1) Theme.text.primary else textColor
        val resolvedAccent = if (accentColor == -1) Theme.text.accent else accentColor

        val displayText = when (state) {
            State.EDITING -> if (cursorVisible) editBuffer + "|" else editBuffer
            else          -> text
        }
        val activeColor = if (state == State.EDITING) resolvedAccent else resolvedText

        // Ширина считается всегда от базового текста без курсора — иначе позиция прыгает при мигании
        val baseText = if (state == State.EDITING) editBuffer else text
        val rawTextW = (tr.getWidth(baseText) * textScale).toInt()

        // Позиция текста по X в зависимости от выравнивания
        val textX = when (align) {
            TextAlign.LEFT   -> ctx.x
            TextAlign.CENTER -> ctx.cx - rawTextW / 2
            TextAlign.RIGHT  -> ctx.right - rawTextW
        }
        val textY = ctx.cy - ((8f * textScale) / 2f).toInt()

        // ── Текст ─────────────────────────────────────────────────────────
        ctx.drawContext.withClip(ctx.x, ctx.y, ctx.width, ctx.height) {
            drawScaledText(
                displayText, textX, textY,
                scale = textScale, color = activeColor,
                align = TextAlign.LEFT, shadow = false
            )
        }

        // ── Иконка — сразу после текста ───────────────────────────────────
        val icon = if (state == State.EDITING) "✓" else "✎"
        val iconColor = if (state == State.EDITING) resolvedAccent else Theme.text.disabled
        val iconX = textX + rawTextW + iconGap
        ctx.drawContext.drawScaledText(
            icon, iconX, textY,
            scale = textScale * 0.85f,
            color = iconColor,
            align = TextAlign.LEFT,
            shadow = false
        )

        // ── Divider — прямо под текстом ───────────────────────────────────
        val progress = revealProgress.current
        if (progress > 0.005f) {
            val lineY = textY + (8f * textScale).toInt() + 1
            val lineW = ctx.width
            val resolvedAccent = if (accentColor == -1) Theme.text.accent else accentColor
            val ap = accentProgress.current  // 0 = белый, 1 = акцентный

            for (i in 0 until lineW) {
                val t = i.toFloat() / lineW.coerceAtLeast(1)
                val edgeDist = minOf(t, 1f - t)

                // Статичный градиент opacity
                val staticFade = 0.35f
                val staticAlpha = if (edgeDist < staticFade) edgeDist / staticFade else 1f

                // Анимация раскрытия
                val revealThreshold = 0.5f - progress * 0.5f
                val revealAlpha = if (edgeDist < revealThreshold) 0f
                                  else ((edgeDist - revealThreshold) / (0.5f - revealThreshold).coerceAtLeast(0.001f)).coerceIn(0f, 1f)

                val finalAlpha = staticAlpha * revealAlpha
                if (finalAlpha > 0.01f) {
                    // Анимация цвета: "раскрытие" акцентного цвета от центра к краям
                    // accentThreshold: при ap=0 акцент нигде, при ap=1 акцент везде
                    val accentThreshold = 0.5f - ap * 0.5f
                    val accentRatio = if (edgeDist < accentThreshold) 0f
                                     else ((edgeDist - accentThreshold) / (0.5f - accentThreshold).coerceAtLeast(0.001f)).coerceIn(0f, 1f)

                    val pixelColor = lerpColor(Theme.text.primary, resolvedAccent, accentRatio)
                    ctx.drawContext.fillRect(ctx.x + i, lineY, 1, 1, pixelColor.withAlpha(finalAlpha))
                }
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
        revealProgress.target = 1f
        accentProgress.target = 1f   // цвет раскрывается от центра к краям
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
        revealProgress.target = 0f
        accentProgress.target = 0f   // цвет схлопывается к центру
        cursorVisible = false
    }

    // ── Проброс событий ───────────────────────────────────────────────────

    fun handleClick(mouseX: Int, mouseY: Int, button: Int): Boolean =
        click.handle(mouseX, mouseY, button, hover.isHovered)

    fun handleChar(char: Char): Boolean =
        focus.handleCharTyped(char)

    fun handleKey(key: Int, mods: Int): Boolean =
        focus.handleKeyPress(key, mods)
}
