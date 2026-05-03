package omc.boundbyfate.client.gui.widgets

import omc.boundbyfate.client.gui.components.Clickable
import omc.boundbyfate.client.gui.components.Hoverable
import omc.boundbyfate.client.gui.core.*

/**
 * Универсальная кнопка с лейблом.
 *
 * Размер полностью определяется [RenderContext] — виджет занимает весь ctx.width × ctx.height.
 * Колбек [onClick] задаётся снаружи (в экране или layout-сборщике).
 *
 * ## Использование
 * ```kotlin
 * val btn = BbfButton("Мировоззрение")
 * btn.onClick { openAlignmentScreen() }
 *
 * // В VBoxLayout:
 * vbox(gap = 2) {
 *     add(btn, height = segH / 4)
 * }
 * ```
 */
class BbfButton(
    var label: String,
    var enabled: Boolean = true
) : BbfWidget() {

    // ── Поведение ─────────────────────────────────────────────────────────

    val hover = Hoverable()
    val click = Clickable(enabled = { enabled })

    // ── Анимации ──────────────────────────────────────────────────────────

    /**
     * Масштаб текста при наведении — только текст увеличивается, не кнопка целиком.
     * Ширина кнопки остаётся фиксированной, высота определяется контейнером (AccordionList).
     */
    private val textScale = animFloat(1f, speed = 0.2f)

    // ── API ───────────────────────────────────────────────────────────────

    /** Регистрирует колбек клика. */
    fun onClick(block: () -> Unit) = click.onClick(block)

    // ── Tick ──────────────────────────────────────────────────────────────

    override fun tick(ctx: RenderContext) {
        hover.update(ctx)
        textScale.target = when {
            !enabled        -> 1f
            hover.isHovered -> 1.08f
            else            -> 1f
        }
        tickAll(ctx.delta)
    }

    // ── Render ────────────────────────────────────────────────────────────

    override fun render(ctx: RenderContext) {
        val bg = when {
            !enabled        -> Theme.button.disabled
            hover.isHovered -> Theme.button.hovered
            else            -> Theme.button.normal
        }
        val textColor = if (enabled) Theme.button.text else Theme.button.textDisabled

        // Фон и рамка — занимают весь контекст без масштабирования
        ctx.drawContext.fillRectWithBorder(ctx.x, ctx.y, ctx.width, ctx.height, bg, Theme.panel.border)

        // Текст — масштабируется только сам текст, не кнопка
        ctx.drawContext.drawScaledText(
            label,
            ctx.cx, ctx.cy - (4f * textScale.current).toInt(),
            scale  = textScale.current,
            color  = textColor,
            align  = TextAlign.CENTER,
            shadow = false
        )
    }

    // ── Клики (пробрасываются из экрана) ──────────────────────────────────

    fun handleClick(mouseX: Int, mouseY: Int, button: Int): Boolean =
        click.handle(mouseX, mouseY, button, hover.isHovered)
}
