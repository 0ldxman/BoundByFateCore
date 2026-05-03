package omc.boundbyfate.client.gui.widgets

import omc.boundbyfate.client.gui.components.Clickable
import omc.boundbyfate.client.gui.components.Hoverable
import omc.boundbyfate.client.gui.core.*

/**
 * Универсальная шкала из чекбоксов.
 *
 * Используется для жизненной силы, но гибкая — можно любое количество.
 *
 * ## Использование
 *
 * ```kotlin
 * val lifeForce = CheckboxScaleWidget(
 *     total = 5,
 *     checked = 3,
 *     checkboxSize = 16,
 *     gap = 4
 * )
 * lifeForce.onCheckedChanged = { newChecked -> println("Life force: $newChecked") }
 * ```
 */
class CheckboxScaleWidget(
    var total: Int = 5,
    var checked: Int = 5,
    var checkboxSize: Int = 16,
    var gap: Int = 4,
    var checkedColor: Int = 0xFF55FF55.toInt(),
    var uncheckedColor: Int = Theme.panel.border
) : BbfWidget() {

    private val hovers = List(total) { Hoverable(playSoundOnEnter = false) }
    private val clicks = List(total) { Clickable() }
    private val scales = List(total) { animFloat(1f, speed = 0.2f) }

    /** Колбек на изменение количества отмеченных. */
    var onCheckedChanged: ((Int) -> Unit)? = null

    init {
        clicks.forEachIndexed { index, click ->
            click.onClick {
                // Клик переключает состояние: если кликнули на отмеченный — снимаем до него
                // если на неотмеченный — отмечаем до него
                checked = if (index < checked) index else index + 1
                onCheckedChanged?.invoke(checked)
            }
        }
    }

    override fun tick(ctx: RenderContext) {
        val totalWidth = total * checkboxSize + (total - 1) * gap
        val startX = ctx.x + (ctx.width - totalWidth) / 2

        hovers.forEachIndexed { index, hover ->
            val boxX = startX + index * (checkboxSize + gap)
            hover.update(ctx.mouseX, ctx.mouseY, boxX, ctx.y, checkboxSize, checkboxSize)
            scales[index].target = if (hover.isHovered) 1.15f else 1f
        }

        tickAll(ctx.delta)
    }

    override fun render(ctx: RenderContext) {
        val totalWidth = total * checkboxSize + (total - 1) * gap
        val startX = ctx.x + (ctx.width - totalWidth) / 2

        repeat(total) { index ->
            val boxX = startX + index * (checkboxSize + gap)
            val centerX = boxX + checkboxSize / 2
            val centerY = ctx.y + checkboxSize / 2

            ctx.drawContext.transform(
                pivotX = centerX.toFloat(),
                pivotY = centerY.toFloat(),
                scale = scales[index].current
            ) {
                val isChecked = index < checked
                val color = if (isChecked) checkedColor else uncheckedColor

                // Рамка
                strokeRect(boxX, ctx.y, checkboxSize, checkboxSize, Theme.panel.border, 1)

                // Заливка если отмечен
                if (isChecked) {
                    fillRect(boxX + 2, ctx.y + 2, checkboxSize - 4, checkboxSize - 4, color)
                }
            }
        }
    }

    fun handleClick(mouseX: Int, mouseY: Int, button: Int): Boolean {
        val totalWidth = total * checkboxSize + (total - 1) * gap
        val startX = (width - totalWidth) / 2

        clicks.forEachIndexed { index, click ->
            val boxX = startX + index * (checkboxSize + gap)
            val isHovered = mouseX in boxX..(boxX + checkboxSize) && 
                           mouseY in 0..checkboxSize
            if (click.handle(mouseX, mouseY, button, isHovered)) return true
        }
        return false
    }

    val width get() = total * checkboxSize + (total - 1) * gap
}
