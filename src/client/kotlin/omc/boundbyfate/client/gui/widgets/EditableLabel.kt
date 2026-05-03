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
     * Анимация: при появлении 0→1 (раскрытие от центра к краям).
     *           при исчезновении 1→0 (схлопывание к центру).
     */
    private val revealProgress = animFloat(0f, speed = 0.18f)

    /** Цвет divider — интерполируется между text.primary и accent. */
    private val dividerColor = animColor(Theme.text.primary, speed = 0.15f)

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
                    // При hover divider белый (цвет текста)
                    dividerColor.target = Theme.text.primary
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
            State.EDITING -> editBuffer + if (cursorVisible) "|" else " "
            else          -> text
        }
        val activeColor = if (state == State.EDITING) resolvedAccent else resolvedText

        // Ширина текста в пикселях
        val rawTextW = (tr.getWidth(displayText) * textScale).toInt()

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
            // lineY = нижняя граница текста + 1px gap
            val lineY = textY + (8f * textScale).toInt() + 1
            val lineColor = dividerColor.current
            val lineW = ctx.width

            for (i in 0 until lineW) {
                val t = i.toFloat() / lineW.coerceAtLeast(1)
                // Расстояние от ближайшего края (0 на краях, 0.5 в центре)
                val edgeDist = minOf(t, 1f - t)
                // Статичный градиент opacity: центр непрозрачный, края прозрачные
                // fadeRatio = 0.35f — 35% с каждой стороны уходит в прозрачность
                val staticFade = 0.35f
                val staticAlpha = if (edgeDist < staticFade) edgeDist / staticFade else 1f

                // Анимация раскрытия: пиксель виден только если он "раскрыт"
                // При progress=0 виден только центральный пиксель (edgeDist=0.5)
                // При progress=1 видна вся линия
                // Порог видимости: edgeDist >= (0.5f - progress * 0.5f)
                val revealThreshold = 0.5f - progress * 0.5f
                val revealAlpha = if (edgeDist < revealThreshold) 0f
                                  else ((edgeDist - revealThreshold) / (0.5f - revealThreshold).coerceAtLeast(0.001f)).coerceIn(0f, 1f)

                val finalAlpha = staticAlpha * revealAlpha
                if (finalAlpha > 0.01f) {
                    ctx.drawContext.fillRect(ctx.x + i, lineY, 1, 1, lineColor.withAlpha(finalAlpha))
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
        val resolvedAccent = if (accentColor == -1) Theme.text.accent else accentColor
        dividerColor.target = resolvedAccent
        revealProgress.target = 1f
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
        // Возвращаем цвет к белому перед исчезновением
        dividerColor.target = Theme.text.primary
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
