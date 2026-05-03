package omc.boundbyfate.client.gui.widgets.character

import omc.boundbyfate.client.gui.components.Clickable
import omc.boundbyfate.client.gui.components.Hoverable
import omc.boundbyfate.client.gui.core.*

/**
 * Чекбокс владения с 3 состояниями.
 *
 * - NONE (пустой кружочек) — нет владения
 * - PROFICIENT (зелёный кружочек) — владение
 * - EXPERTISE (синий кружочек) — мастерство
 *
 * ## Использование
 *
 * ```kotlin
 * val checkbox = ProficiencyCheckbox(state = ProficiencyState.PROFICIENT)
 * checkbox.onStateChanged = { newState -> println("New state: $newState") }
 * ```
 */
class ProficiencyCheckbox(
    var state: ProficiencyState = ProficiencyState.NONE,
    var size: Int = 12
) : BbfWidget() {

    private val hover = Hoverable(playSoundOnEnter = false)
    private val click = Clickable()
    private val scale = animFloat(1f, speed = 0.2f)

    /** Колбек на изменение состояния. */
    var onStateChanged: ((ProficiencyState) -> Unit)? = null

    init {
        click.onClick {
            state = when (state) {
                ProficiencyState.NONE -> ProficiencyState.PROFICIENT
                ProficiencyState.PROFICIENT -> ProficiencyState.EXPERTISE
                ProficiencyState.EXPERTISE -> ProficiencyState.NONE
            }
            onStateChanged?.invoke(state)
        }
    }

    override fun tick(ctx: RenderContext) {
        hover.update(ctx)
        scale.target = if (hover.isHovered) 1.15f else 1f
        tickAll(ctx.delta)
    }

    override fun render(ctx: RenderContext) {
        val centerX = ctx.x + size / 2
        val centerY = ctx.y + size / 2

        ctx.drawContext.transform(
            pivotX = centerX.toFloat(),
            pivotY = centerY.toFloat(),
            scale = scale.current
        ) {
            // Внешний круг (рамка)
            drawCircle(centerX, centerY, size / 2, Theme.panel.border, filled = false)

            // Внутренний круг (заливка по состоянию)
            when (state) {
                ProficiencyState.NONE -> {
                    // Пустой
                }
                ProficiencyState.PROFICIENT -> {
                    drawCircle(centerX, centerY, size / 2 - 2, 0xFF55FF55.toInt(), filled = true)
                }
                ProficiencyState.EXPERTISE -> {
                    drawCircle(centerX, centerY, size / 2 - 2, 0xFF5555FF.toInt(), filled = true)
                }
            }
        }
    }

    fun handleClick(mouseX: Int, mouseY: Int, button: Int): Boolean =
        click.handle(mouseX, mouseY, button, hover.isHovered)

    /**
     * Рисует круг (заливка или рамка).
     */
    private fun net.minecraft.client.gui.DrawContext.drawCircle(
        cx: Int, cy: Int, radius: Int, color: Int, filled: Boolean
    ) {
        if (filled) {
            // Простая заливка квадратом (для прототипа)
            // TODO: заменить на настоящий круг через шейдер или текстуру
            fillRect(cx - radius, cy - radius, radius * 2, radius * 2, color)
        } else {
            // Рамка квадратом
            strokeRect(cx - radius, cy - radius, radius * 2, radius * 2, color, 1)
        }
    }
}

/**
 * Состояние владения.
 */
enum class ProficiencyState {
    /** Нет владения. */
    NONE,
    /** Владение (proficiency). */
    PROFICIENT,
    /** Мастерство (expertise). */
    EXPERTISE
}
