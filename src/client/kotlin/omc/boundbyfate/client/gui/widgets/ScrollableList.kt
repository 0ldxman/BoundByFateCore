package omc.boundbyfate.client.gui.widgets

import omc.boundbyfate.client.gui.components.Scrollable
import omc.boundbyfate.client.gui.core.*

/**
 * Универсальный скроллируемый список.
 *
 * Принимает список элементов любого типа и функцию для создания виджета из элемента.
 *
 * ## Использование
 *
 * ```kotlin
 * val skills = listOf("Акробатика", "Атлетика", "Обман")
 * val skillList = ScrollableList(
 *     items = skills,
 *     itemHeight = 18,
 *     createWidget = { skill -> SkillRowWidget(name = skill, bonus = 5) }
 * )
 * ```
 */
class ScrollableList<T>(
    var items: List<T> = emptyList(),
    var itemHeight: Int = 18,
    var gap: Int = 2,
    var createWidget: (T) -> BbfWidget
) : BbfWidget() {

    private val scroll = Scrollable()
    private val widgets = mutableMapOf<T, BbfWidget>()

    override fun tick(ctx: RenderContext) {
        // Обновляем виджеты если список изменился
        val currentKeys = widgets.keys.toSet()
        val newKeys = items.toSet()

        // Удаляем старые
        (currentKeys - newKeys).forEach { widgets.remove(it) }

        // Добавляем новые
        (newKeys - currentKeys).forEach { item ->
            widgets[item] = createWidget(item)
        }

        // Обновляем scroll
        scroll.contentHeight = items.size * (itemHeight + gap)
        scroll.viewHeight = ctx.height
        scroll.tick(ctx.delta)

        // Тикаем видимые виджеты
        val startIndex = (scroll.offset / (itemHeight + gap)).toInt().coerceAtLeast(0)
        val endIndex = ((scroll.offset + ctx.height) / (itemHeight + gap)).toInt() + 1

        items.subList(startIndex.coerceAtMost(items.size), endIndex.coerceAtMost(items.size))
            .forEach { item ->
                val widget = widgets[item] ?: return@forEach
                val index = items.indexOf(item)
                val y = index * (itemHeight + gap) - scroll.offset.toInt()
                val childCtx = ctx.child(0, y, ctx.width - 8, itemHeight)
                widget.tick(childCtx)
            }

        tickAll(ctx.delta)
    }

    override fun render(ctx: RenderContext) {
        ctx.drawContext.withClip(ctx.x, ctx.y, ctx.width, ctx.height) {
            val startIndex = (scroll.offset / (itemHeight + gap)).toInt().coerceAtLeast(0)
            val endIndex = ((scroll.offset + ctx.height) / (itemHeight + gap)).toInt() + 1

            items.subList(startIndex.coerceAtMost(items.size), endIndex.coerceAtMost(items.size))
                .forEach { item ->
                    val widget = widgets[item] ?: return@forEach
                    val index = items.indexOf(item)
                    val y = index * (itemHeight + gap) - scroll.offset.toInt()
                    val childCtx = ctx.child(0, y, ctx.width - 8, itemHeight)
                    widget.render(childCtx)
                }
        }

        // Скроллбар
        scroll.renderScrollbar(ctx.drawContext, ctx.right - 8, ctx.y, 8, ctx.height)
    }

    fun handleScroll(amount: Double): Boolean = scroll.handleScroll(amount)

    fun handleClick(mouseX: Int, mouseY: Int, button: Int): Boolean {
        val startIndex = (scroll.offset / (itemHeight + gap)).toInt().coerceAtLeast(0)
        val endIndex = ((scroll.offset + ctx.height) / (itemHeight + gap)).toInt() + 1

        items.subList(startIndex.coerceAtMost(items.size), endIndex.coerceAtMost(items.size))
            .forEach { item ->
                val widget = widgets[item] ?: return@forEach
                val index = items.indexOf(item)
                val y = index * (itemHeight + gap) - scroll.offset.toInt()
                
                // Проверяем клик по виджету
                if (widget is SkillRowWidget) {
                    if (widget.handleClick(mouseX, mouseY - y, button)) return true
                }
            }
        return false
    }
}
