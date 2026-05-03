package omc.boundbyfate.client.gui.widgets.character

import omc.boundbyfate.client.gui.components.Hoverable
import omc.boundbyfate.client.gui.core.*

/**
 * Строка навыка или спасброска.
 *
 * Формат: [Название] [Бонус] [Чекбокс]
 *
 * ## Использование
 *
 * ```kotlin
 * val acrobatics = SkillRowWidget(
 *     name = "Акробатика",
 *     bonus = 5,
 *     state = ProficiencyState.PROFICIENT
 * )
 * ```
 */
class SkillRowWidget(
    var name: String,
    var bonus: Int = 0,
    state: ProficiencyState = ProficiencyState.NONE,
    var height: Int = 18
) : BbfWidget() {

    private val checkbox = ProficiencyCheckbox(state)
    private val hover = Hoverable(playSoundOnEnter = false)
    private val bgAlpha = animFloat(0f, speed = 0.15f)

    /** Колбек на изменение состояния владения. */
    var onProficiencyChanged: ((ProficiencyState) -> Unit)?
        get() = checkbox.onStateChanged
        set(value) { checkbox.onStateChanged = value }

    /** Текущее состояние владения. */
    var proficiencyState: ProficiencyState
        get() = checkbox.state
        set(value) { checkbox.state = value }

    override fun tick(ctx: RenderContext) {
        hover.update(ctx)
        bgAlpha.target = if (hover.isHovered) 0.3f else 0f

        val checkboxCtx = ctx.child(
            offsetX = ctx.width - checkbox.size - 4,
            offsetY = (height - checkbox.size) / 2,
            w = checkbox.size,
            h = checkbox.size
        )
        checkbox.tick(checkboxCtx)

        tickAll(ctx.delta)
    }

    override fun render(ctx: RenderContext) {
        // Фон при наведении
        if (bgAlpha.current > 0.01f) {
            ctx.drawContext.fillRect(ctx.x, ctx.y, ctx.width, height, 
                Theme.panel.backgroundLight.withAlpha(bgAlpha.current))
        }

        // Название
        ctx.drawContext.drawScaledText(name, ctx.x + 4, ctx.y + height / 2 - 4, 
            scale = 0.8f, color = Theme.text.primary)

        // Бонус
        val bonusText = formatBonus(bonus)
        val bonusX = ctx.x + ctx.width - checkbox.size - 30
        ctx.drawContext.drawScaledText(bonusText, bonusX, ctx.y + height / 2 - 4, 
            scale = 0.8f, color = Theme.text.accent, align = TextAlign.RIGHT)

        // Чекбокс
        val checkboxCtx = ctx.child(
            offsetX = ctx.width - checkbox.size - 4,
            offsetY = (height - checkbox.size) / 2,
            w = checkbox.size,
            h = checkbox.size
        )
        checkbox.render(checkboxCtx)
    }

    fun handleClick(mouseX: Int, mouseY: Int, button: Int): Boolean {
        val checkboxX = checkbox.size + 4
        val checkboxY = (height - checkbox.size) / 2
        return checkbox.handleClick(mouseX - checkboxX, mouseY - checkboxY, button)
    }

    private fun formatBonus(value: Int): String = if (value >= 0) "+$value" else "$value"
}
