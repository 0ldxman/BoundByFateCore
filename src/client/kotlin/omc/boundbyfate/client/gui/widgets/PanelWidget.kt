package omc.boundbyfate.client.gui.widgets

import omc.boundbyfate.client.gui.components.Clickable
import omc.boundbyfate.client.gui.components.Hoverable
import omc.boundbyfate.client.gui.core.*

/**
 * Базовый виджет-панель. Рисует фон и рамку, передаёт дочернему виджету
 * контекст уменьшенный на [padding].
 *
 * Если задан [onAdd] — в правом верхнем углу рисуется квадратная кнопка "+".
 * Высота кнопки = высота строки заголовка (ADD_BTN_SIZE).
 *
 * ## Использование
 * ```kotlin
 * PanelWidget(
 *     title = "Особенности",
 *     content = scrollable,
 *     padding = 3,
 *     onAdd = { openAddFeatureDialog() }
 * )
 * ```
 */
open class PanelWidget(
    var bgColor: Int = Theme.panel.background,
    var borderColor: Int = Theme.panel.border,
    var borderThickness: Int = 1,
    var padding: Int = 0,
    var title: String? = null,
    var content: BbfWidget? = null,
    var onAdd: (() -> Unit)? = null
) : BbfWidget() {

    companion object {
        /** Размер квадратной кнопки "+" в пикселях. */
        const val ADD_BTN_SIZE = 10
        /** Высота строки заголовка (текст + отступы). */
        const val HEADER_H = 12
    }

    // ── Поведение кнопки "+" ──────────────────────────────────────────────

    private val addHover  = Hoverable(playSoundOnEnter = true)
    private val addClick  = Clickable()

    init {
        addClick.onClick { onAdd?.invoke() }
    }

    // ── Tick ──────────────────────────────────────────────────────────────

    override fun tick(ctx: RenderContext) {
        if (onAdd != null) {
            val (bx, by) = addBtnPos(ctx)
            addHover.update(ctx.mouseX, ctx.mouseY, bx, by, ADD_BTN_SIZE, ADD_BTN_SIZE)
        }
        content?.tick(contentCtx(ctx))
        tickAll(ctx.delta)
    }

    // ── Render ────────────────────────────────────────────────────────────

    override fun render(ctx: RenderContext) {
        // Фон и рамка
        ctx.drawContext.fillRectWithBorder(
            ctx.x, ctx.y, ctx.width, ctx.height,
            bg = bgColor, border = borderColor, thickness = borderThickness
        )

        // Заголовок + кнопка "+"
        if (title != null || onAdd != null) {
            renderHeader(ctx)
        }

        // Хук для дочерних классов
        renderContent(ctx)

        // Содержимое
        content?.render(contentCtx(ctx))
    }

    private fun renderHeader(ctx: RenderContext) {
        val textY = ctx.y + padding + (HEADER_H - 6) / 2  // вертикальное центрирование текста

        // Заголовок слева
        if (title != null) {
            ctx.drawContext.drawScaledText(
                text = title!!, x = ctx.x + padding + 2, y = textY,
                scale = 0.8f, color = Theme.text.secondary, shadow = false
            )
        }

        // Кнопка "+" справа
        if (onAdd != null) {
            val (bx, by) = addBtnPos(ctx)
            val btnBg = if (addHover.isHovered) Theme.button.hovered else Theme.button.normal
            ctx.drawContext.fillRectWithBorder(bx, by, ADD_BTN_SIZE, ADD_BTN_SIZE, btnBg, Theme.panel.border)
            ctx.drawContext.drawScaledText(
                text = "+", x = bx + ADD_BTN_SIZE / 2, y = by + 1,
                scale = 0.8f, color = Theme.text.primary,
                align = TextAlign.CENTER, shadow = false
            )
        }
    }

    /**
     * Хук для дочерних классов — рисуется поверх фона, до [content].
     */
    open fun renderContent(ctx: RenderContext) {}

    // ── Клики ─────────────────────────────────────────────────────────────

    fun handleClick(mouseX: Int, mouseY: Int, button: Int): Boolean =
        addClick.handle(mouseX, mouseY, button, addHover.isHovered)

    // ── Вспомогательные ───────────────────────────────────────────────────

    /** Позиция кнопки "+" — правый верхний угол с учётом padding. */
    private fun addBtnPos(ctx: RenderContext): Pair<Int, Int> {
        val bx = ctx.right - padding - ADD_BTN_SIZE
        val by = ctx.y + padding + (HEADER_H - ADD_BTN_SIZE) / 2
        return bx to by
    }

    /**
     * Контекст для дочернего виджета.
     * Если есть заголовок или кнопка "+" — смещаем вниз на HEADER_H.
     */
    protected fun contentCtx(ctx: RenderContext): RenderContext {
        val headerOffset = if (title != null || onAdd != null) HEADER_H else 0
        return ctx.child(
            offsetX = padding,
            offsetY = padding + headerOffset,
            w = ctx.width  - padding * 2,
            h = ctx.height - padding * 2 - headerOffset
        )
    }
}
