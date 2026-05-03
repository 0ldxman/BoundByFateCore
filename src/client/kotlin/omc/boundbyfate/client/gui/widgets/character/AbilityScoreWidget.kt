package omc.boundbyfate.client.gui.widgets.character

import omc.boundbyfate.client.gui.components.Clickable
import omc.boundbyfate.client.gui.components.Hoverable
import omc.boundbyfate.client.gui.core.*
import omc.boundbyfate.client.gui.widgets.AnimatedTooltip

/**
 * Виджет характеристики (Ability Score).
 *
 * Квадратный бокс 43x43 с кнопками +/- по бокам (11x43 каждая).
 *
 * Структура:
 * - Верх: название (СИЛ, ЛОВ и т.д.)
 * - Центр: базовое значение + модификаторы (10+2)
 * - Низ: итоговый бонус (+1)
 *
 * ## Использование
 *
 * ```kotlin
 * val strWidget = AbilityScoreWidget(
 *     name = "СИЛ",
 *     baseValue = 10,
 *     modifiers = 2
 * )
 *
 * strWidget.onValueChanged = { newBase -> println("New STR: $newBase") }
 * ```
 */
class AbilityScoreWidget(
    var name: String,
    var baseValue: Int = 10,
    var modifiers: Int = 0,
    var minValue: Int = 1,
    var maxValue: Int = 30
) : BbfWidget() {

    companion object {
        const val BOX_SIZE = 43
        const val BUTTON_WIDTH = 11
        const val BUTTON_HEIGHT = 43
        const val TOTAL_WIDTH = BUTTON_WIDTH + BOX_SIZE + BUTTON_WIDTH
    }

    private val hoverBox = Hoverable()
    private val hoverMinus = Hoverable()
    private val hoverPlus = Hoverable()
    
    private val clickMinus = Clickable()
    private val clickPlus = Clickable()

    private val scaleBox = animFloat(1f, speed = 0.15f)
    private val scaleMinus = animFloat(1f, speed = 0.2f)
    private val scalePlus = animFloat(1f, speed = 0.2f)

    private val tooltip = AnimatedTooltip { 
        net.minecraft.text.Text.literal("$name: ${totalValue} (${formatBonus(bonus)})")
    }

    /** Колбек на изменение базового значения. */
    var onValueChanged: ((newBase: Int) -> Unit)? = null

    /** Итоговое значение (база + модификаторы). */
    val totalValue get() = baseValue + modifiers

    /** Бонус характеристики (для бросков). */
    val bonus get() = (totalValue - 10) / 2

    init {
        clickMinus.onClick {
            if (baseValue > minValue) {
                baseValue--
                onValueChanged?.invoke(baseValue)
            }
        }

        clickPlus.onClick {
            if (baseValue < maxValue) {
                baseValue++
                onValueChanged?.invoke(baseValue)
            }
        }
    }

    override fun tick(ctx: RenderContext) {
        // Бокс характеристики
        hoverBox.update(ctx.mouseX, ctx.mouseY, 
            ctx.x + BUTTON_WIDTH, ctx.y, BOX_SIZE, BOX_SIZE)
        scaleBox.target = if (hoverBox.isHovered) 1.05f else 1f

        // Кнопка минус
        hoverMinus.update(ctx.mouseX, ctx.mouseY, 
            ctx.x, ctx.y, BUTTON_WIDTH, BUTTON_HEIGHT)
        scaleMinus.target = if (hoverMinus.isHovered) 1.1f else 1f

        // Кнопка плюс
        hoverPlus.update(ctx.mouseX, ctx.mouseY, 
            ctx.x + BUTTON_WIDTH + BOX_SIZE, ctx.y, BUTTON_WIDTH, BUTTON_HEIGHT)
        scalePlus.target = if (hoverPlus.isHovered) 1.1f else 1f

        tooltip.update(hoverBox.isHovered, ctx.delta)
        tickAll(ctx.delta)
    }

    override fun render(ctx: RenderContext) {
        val minusX = ctx.x
        val boxX = ctx.x + BUTTON_WIDTH
        val plusX = ctx.x + BUTTON_WIDTH + BOX_SIZE

        // Кнопка минус
        ctx.drawContext.transform(
            pivotX = (minusX + BUTTON_WIDTH / 2).toFloat(),
            pivotY = (ctx.y + BUTTON_HEIGHT / 2).toFloat(),
            scale = scaleMinus.current
        ) {
            val color = if (baseValue <= minValue) Theme.button.disabled else Theme.button.normal
            fillRect(minusX, ctx.y, BUTTON_WIDTH, BUTTON_HEIGHT, color)
            drawScaledText("-", minusX + BUTTON_WIDTH / 2, ctx.y + BUTTON_HEIGHT / 2 - 4, 
                color = Theme.text.primary, align = TextAlign.CENTER)
        }

        // Бокс характеристики
        ctx.drawContext.transform(
            pivotX = (boxX + BOX_SIZE / 2).toFloat(),
            pivotY = (ctx.y + BOX_SIZE / 2).toFloat(),
            scale = scaleBox.current
        ) {
            // Фон
            fillRectWithBorder(boxX, ctx.y, BOX_SIZE, BOX_SIZE, 
                Theme.panel.background, Theme.panel.border)

            // Название (верх)
            drawScaledText(name, boxX + BOX_SIZE / 2, ctx.y + 3, 
                scale = 0.7f, color = Theme.text.secondary, align = TextAlign.CENTER)

            // Значение (центр)
            val valueText = if (modifiers != 0) "$baseValue+$modifiers" else "$baseValue"
            drawScaledText(valueText, boxX + BOX_SIZE / 2, ctx.y + 15, 
                scale = 0.9f, color = Theme.text.primary, align = TextAlign.CENTER)

            // Бонус (низ)
            val bonusText = formatBonus(bonus)
            drawScaledText(bonusText, boxX + BOX_SIZE / 2, ctx.y + 30, 
                scale = 0.8f, color = Theme.text.accent, align = TextAlign.CENTER)
        }

        // Кнопка плюс
        ctx.drawContext.transform(
            pivotX = (plusX + BUTTON_WIDTH / 2).toFloat(),
            pivotY = (ctx.y + BUTTON_HEIGHT / 2).toFloat(),
            scale = scalePlus.current
        ) {
            val color = if (baseValue >= maxValue) Theme.button.disabled else Theme.button.normal
            fillRect(plusX, ctx.y, BUTTON_WIDTH, BUTTON_HEIGHT, color)
            drawScaledText("+", plusX + BUTTON_WIDTH / 2, ctx.y + BUTTON_HEIGHT / 2 - 4, 
                color = Theme.text.primary, align = TextAlign.CENTER)
        }

        // Тултип
        tooltip.render(ctx)
    }

    fun handleClick(mouseX: Int, mouseY: Int, button: Int): Boolean {
        if (clickMinus.handle(mouseX, mouseY, button, hoverMinus.isHovered)) return true
        if (clickPlus.handle(mouseX, mouseY, button, hoverPlus.isHovered)) return true
        return false
    }

    private fun formatBonus(value: Int): String = if (value >= 0) "+$value" else "$value"
}
