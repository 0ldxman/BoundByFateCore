package omc.boundbyfate.client.gui.widgets

import omc.boundbyfate.client.gui.core.*

/**
 * Базовый виджет-панель. Рисует фон и рамку, передаёт дочернему виджету
 * контекст уменьшенный на [padding].
 *
 * Является базовым классом для всех окон и контейнеров с визуальным оформлением.
 * [ScrollableBlock], списки и другие функциональные виджеты не рисуют фон сами —
 * они оборачиваются в [PanelWidget].
 *
 * ## Использование
 * ```kotlin
 * // Простая панель-заглушка
 * PanelWidget()
 *
 * // Панель с содержимым
 * PanelWidget(content = scrollableBlock, padding = 2)
 *
 * // Панель с заголовком
 * PanelWidget(title = "Спасброски", content = savingThrowsList, padding = 2)
 *
 * // Наследование для специфичного функционала
 * class CharacterHeaderPanel : PanelWidget() {
 *     override fun renderContent(ctx: RenderContext) { ... }
 * }
 * ```
 */
open class PanelWidget(
    var bgColor: Int = Theme.panel.background,
    var borderColor: Int = Theme.panel.border,
    var borderThickness: Int = 1,
    var padding: Int = 0,
    var title: String? = null,
    var content: BbfWidget? = null
) : BbfWidget() {

    // ── Tick ──────────────────────────────────────────────────────────────

    override fun tick(ctx: RenderContext) {
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

        // Заголовок (если есть)
        if (title != null) {
            ctx.drawContext.drawScaledText(
                text = title!!, x = ctx.x + padding + 2, y = ctx.y + padding + 2,
                scale = 0.8f, color = Theme.text.secondary, shadow = false
            )
        }

        // Содержимое
        renderContent(ctx)
        content?.render(contentCtx(ctx))
    }

    /**
     * Хук для дочерних классов — рисуется поверх фона, до [content].
     * Переопредели для добавления кастомного содержимого без наследования.
     */
    open fun renderContent(ctx: RenderContext) {}

    // ── Вспомогательные ───────────────────────────────────────────────────

    /** Контекст для дочернего виджета с учётом padding и заголовка. */
    protected fun contentCtx(ctx: RenderContext): RenderContext {
        val titleOffset = if (title != null) (8f * 0.8f + padding).toInt() else 0
        return ctx.child(
            offsetX = padding,
            offsetY = padding + titleOffset,
            w = ctx.width  - padding * 2,
            h = ctx.height - padding * 2 - titleOffset
        )
    }
}
