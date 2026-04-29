package omc.boundbyfate.client.gui.core

import net.minecraft.client.gui.DrawContext

/**
 * Контекст рендера — несёт позицию, размер, мышь и delta.
 *
 * Виджеты получают контекст вместо отдельных параметров x, y, mouseX, mouseY.
 * Это убирает передачу координат через все уровни вложенности.
 *
 * ## Использование
 *
 * ```kotlin
 * fun render(ctx: RenderContext) {
 *     ctx.drawContext.fillRect(ctx.x, ctx.y, ctx.width, ctx.height, 0xFF000000.toInt())
 *     ctx.drawContext.drawScaledText("Hello", ctx.cx, ctx.y + 5, align = TextAlign.CENTER)
 *
 *     // Дочерний контекст со смещением
 *     val childCtx = ctx.child(offsetX = 8, offsetY = 8, w = ctx.width - 16, h = ctx.height - 16)
 *     child.render(childCtx)
 * }
 * ```
 */
data class RenderContext(
    val drawContext: DrawContext,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val mouseX: Int,
    val mouseY: Int,
    val delta: Float,
    val alpha: Float = 1f,
    val parent: RenderContext? = null
) {
    /** Центр по X. */
    val cx get() = x + width / 2

    /** Центр по Y. */
    val cy get() = y + height / 2

    /** Правый край. */
    val right get() = x + width

    /** Нижний край. */
    val bottom get() = y + height

    /**
     * Проверяет находится ли курсор в заданной области.
     * Координаты относительны текущего контекста.
     */
    fun isHovered(
        localX: Int = 0,
        localY: Int = 0,
        w: Int = width,
        h: Int = height
    ) = mouseX in (x + localX)..(x + localX + w) &&
        mouseY in (y + localY)..(y + localY + h)

    /**
     * Создаёт дочерний контекст со смещением.
     */
    fun child(
        offsetX: Int = 0,
        offsetY: Int = 0,
        w: Int = width - offsetX,
        h: Int = height - offsetY,
        alpha: Float = this.alpha
    ) = copy(
        x = x + offsetX,
        y = y + offsetY,
        width = w,
        height = h,
        alpha = alpha,
        parent = this
    )

    /**
     * Создаёт контекст с абсолютными координатами.
     */
    fun absolute(
        absX: Int,
        absY: Int,
        w: Int,
        h: Int,
        alpha: Float = this.alpha
    ) = copy(x = absX, y = absY, width = w, height = h, alpha = alpha, parent = this)
}
