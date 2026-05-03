package omc.boundbyfate.client.gui.widgets.character

import omc.boundbyfate.client.gui.components.Clickable
import omc.boundbyfate.client.gui.components.Hoverable
import omc.boundbyfate.client.gui.core.*
import kotlin.math.floor

/**
 * Виджет характеристики (StatBox).
 *
 * Структура (пропорции от ширины контекста):
 *   [−]  gap  [    BOX    ]  gap  [+]
 *   btnW  2     остаток       2   btnW
 *
 * Пропорции выведены из эталонных размеров: 11 + 2 + 44 + 2 + 11 = 70px
 *   btnW = ctx.width * (11/70)
 *   boxW = ctx.width - btnW*2 - gap*2   (остаток)
 *   gap  = 2px (фиксированный)
 *
 * Внутри бокса три зоны (пропорции от высоты):
 *   Название:        y + height * 0.12
 *   Значение+мод:    cy (центр)
 *   Бонус:           bottom - height * 0.22
 *
 * ## Использование
 * ```kotlin
 * val str = StatBox(label = "СИЛ", baseValue = 10, modifier = 2)
 * str.onValueChanged = { newBase -> ... }
 * ```
 */
class StatBox(
    var label: String,
    var baseValue: Int = 10,
    var modifier: Int = 0,
    var minValue: Int = 1,
    var maxValue: Int = 30
) : BbfWidget() {

    companion object {
        /** Пропорция кнопки от ширины виджета (11/70). */
        private const val BTN_RATIO = 11f / 70f
        /** Фиксированный зазор между кнопкой и боксом. */
        const val GAP = 2
    }

    // ── Поведение ─────────────────────────────────────────────────────────

    private val hoverMinus = Hoverable()
    private val hoverPlus  = Hoverable()
    private val hoverBox   = Hoverable()

    private val clickMinus = Clickable()
    private val clickPlus  = Clickable()

    // ── Анимации ──────────────────────────────────────────────────────────

    private val scaleMinus = animFloat(1f, speed = 0.2f)
    private val scalePlus  = animFloat(1f, speed = 0.2f)

    // ── Данные ────────────────────────────────────────────────────────────

    /** Итоговое значение = база + модификатор. */
    val totalValue get() = baseValue + modifier

    /** Бонус к броску = floor((total - 10) / 2). */
    val bonus get() = floor((totalValue - 10) / 2.0).toInt()

    /** Колбек при изменении базового значения. */
    var onValueChanged: ((newBase: Int) -> Unit)? = null

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

    // ── Tick ──────────────────────────────────────────────────────────────

    override fun tick(ctx: RenderContext) {
        val (btnW, _, boxX, _) = layout(ctx)

        hoverMinus.update(ctx.mouseX, ctx.mouseY, ctx.x,          ctx.y, btnW,        ctx.height)
        hoverPlus .update(ctx.mouseX, ctx.mouseY, ctx.right - btnW, ctx.y, btnW,       ctx.height)
        hoverBox  .update(ctx.mouseX, ctx.mouseY, boxX,            ctx.y, ctx.width - btnW*2 - GAP*2, ctx.height)

        scaleMinus.target = if (hoverMinus.isHovered) 1.08f else 1f
        scalePlus .target = if (hoverPlus.isHovered)  1.08f else 1f

        tickAll(ctx.delta)
    }

    // ── Render ────────────────────────────────────────────────────────────

    override fun render(ctx: RenderContext) {
        val (btnW, boxW, boxX, plusX) = layout(ctx)

        // ── Кнопка [−] ────────────────────────────────────────────────────
        ctx.drawContext.transform(
            pivotX = (ctx.x + btnW / 2).toFloat(),
            pivotY = ctx.cy.toFloat(),
            scale  = scaleMinus.current
        ) {
            val bg = if (baseValue <= minValue) Theme.button.disabled else
                     if (hoverMinus.isHovered)  Theme.button.hovered  else Theme.button.normal
            fillRectWithBorder(ctx.x, ctx.y, btnW, ctx.height, bg, Theme.panel.border)
            drawScaledText(
                "−", ctx.x + btnW / 2, ctx.cy - 3,
                color = if (baseValue <= minValue) Theme.text.disabled else Theme.text.primary,
                align = TextAlign.CENTER, shadow = false
            )
        }

        // ── Бокс характеристики ───────────────────────────────────────────
        val boxBg = if (hoverBox.isHovered) Theme.panel.backgroundLight else Theme.panel.background
        ctx.drawContext.fillRectWithBorder(boxX, ctx.y, boxW, ctx.height, boxBg, Theme.panel.border)

        // Название (верх бокса)
        val labelY = ctx.y + (ctx.height * 0.12f).toInt()
        ctx.drawContext.drawScaledText(
            label, boxX + boxW / 2, labelY,
            scale = 0.65f, color = Theme.text.secondary,
            align = TextAlign.CENTER, shadow = false
        )

        // Значение + модификатор (центр)
        val valueText = if (modifier != 0) "$baseValue+$modifier" else "$baseValue"
        ctx.drawContext.drawScaledText(
            valueText, boxX + boxW / 2, ctx.cy - 4,
            scale = 0.9f, color = Theme.text.primary,
            align = TextAlign.CENTER, shadow = false
        )

        // Бонус (низ бокса)
        val bonusY = ctx.bottom - (ctx.height * 0.28f).toInt()
        val bonusText = if (bonus >= 0) "+$bonus" else "$bonus"
        ctx.drawContext.drawScaledText(
            bonusText, boxX + boxW / 2, bonusY,
            scale = 0.65f, color = Theme.text.accent,
            align = TextAlign.CENTER, shadow = false
        )

        // ── Кнопка [+] ────────────────────────────────────────────────────
        ctx.drawContext.transform(
            pivotX = (plusX + btnW / 2).toFloat(),
            pivotY = ctx.cy.toFloat(),
            scale  = scalePlus.current
        ) {
            val bg = if (baseValue >= maxValue) Theme.button.disabled else
                     if (hoverPlus.isHovered)   Theme.button.hovered  else Theme.button.normal
            fillRectWithBorder(plusX, ctx.y, btnW, ctx.height, bg, Theme.panel.border)
            drawScaledText(
                "+", plusX + btnW / 2, ctx.cy - 3,
                color = if (baseValue >= maxValue) Theme.text.disabled else Theme.text.primary,
                align = TextAlign.CENTER, shadow = false
            )
        }
    }

    // ── Клики (пробрасываются из экрана) ──────────────────────────────────

    fun handleClick(mouseX: Int, mouseY: Int, button: Int): Boolean {
        if (clickMinus.handle(mouseX, mouseY, button, hoverMinus.isHovered)) return true
        if (clickPlus .handle(mouseX, mouseY, button, hoverPlus.isHovered))  return true
        return false
    }

    // ── Вспомогательные ───────────────────────────────────────────────────

    /**
     * Вычисляет позиции элементов из контекста.
     * Возвращает (btnW, boxW, boxX, plusX).
     */
    private fun layout(ctx: RenderContext): LayoutData {
        val btnW = (ctx.width * BTN_RATIO).toInt()
        val boxW = ctx.width - btnW * 2 - GAP * 2
        val boxX = ctx.x + btnW + GAP
        val plusX = boxX + boxW + GAP
        return LayoutData(btnW, boxW, boxX, plusX)
    }

    private data class LayoutData(val btnW: Int, val boxW: Int, val boxX: Int, val plusX: Int)
}
