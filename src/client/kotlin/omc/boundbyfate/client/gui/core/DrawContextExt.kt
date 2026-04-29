package omc.boundbyfate.client.gui.core

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text

// ── Прямоугольники ────────────────────────────────────────────────────────

/** Заливает прямоугольник. */
fun DrawContext.fillRect(x: Int, y: Int, w: Int, h: Int, color: Int) =
    fill(x, y, x + w, y + h, color)

/** Рисует рамку прямоугольника. */
fun DrawContext.strokeRect(x: Int, y: Int, w: Int, h: Int, color: Int, thickness: Int = 1) {
    fill(x, y, x + w, y + thickness, color)                          // top
    fill(x, y + h - thickness, x + w, y + h, color)                  // bottom
    fill(x, y + thickness, x + thickness, y + h - thickness, color)  // left
    fill(x + w - thickness, y + thickness, x + w, y + h - thickness, color) // right
}

/** Заливает прямоугольник с рамкой. */
fun DrawContext.fillRectWithBorder(
    x: Int, y: Int, w: Int, h: Int,
    bg: Int, border: Int,
    thickness: Int = 1
) {
    fillRect(x, y, w, h, bg)
    strokeRect(x, y, w, h, border, thickness)
}

// ── Clip region ───────────────────────────────────────────────────────────

/**
 * Рисует содержимое с обрезкой по прямоугольнику.
 * Всё что выходит за границы — не отображается.
 */
inline fun DrawContext.withClip(x: Int, y: Int, w: Int, h: Int, block: DrawContext.() -> Unit) {
    enableScissor(x, y, x + w, y + h)
    block()
    disableScissor()
}

// ── Матричные трансформации ───────────────────────────────────────────────

/**
 * Применяет трансформацию к содержимому.
 *
 * @param pivotX точка вращения/масштабирования по X (абсолютная)
 * @param pivotY точка вращения/масштабирования по Y (абсолютная)
 * @param scale масштаб (1f = без изменений)
 * @param scaleX масштаб по X (переопределяет scale)
 * @param scaleY масштаб по Y (переопределяет scale)
 * @param rotation поворот в градусах
 * @param offsetX смещение по X после масштабирования
 * @param offsetY смещение по Y после масштабирования
 * @param alpha прозрачность (1f = непрозрачный)
 */
inline fun DrawContext.transform(
    pivotX: Float = 0f,
    pivotY: Float = 0f,
    scale: Float = 1f,
    scaleX: Float = scale,
    scaleY: Float = scale,
    rotation: Float = 0f,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
    alpha: Float = 1f,
    block: DrawContext.() -> Unit
) {
    if (alpha < 0.005f) return

    val m = matrices
    m.push()

    if (alpha < 1f) {
        RenderSystem.enableBlend()
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha)
    }

    m.translate(pivotX + offsetX, pivotY + offsetY, 0f)
    if (rotation != 0f) m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(rotation))
    if (scaleX != 1f || scaleY != 1f) m.scale(scaleX, scaleY, 1f)
    m.translate(-pivotX, -pivotY, 0f)

    block()

    if (alpha < 1f) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
    }

    m.pop()
}

// ── Текст ─────────────────────────────────────────────────────────────────

enum class TextAlign { LEFT, CENTER, RIGHT }

/**
 * Рисует текст с масштабом и выравниванием.
 * Убирает boilerplate push/translate/scale/pop.
 */
fun DrawContext.drawScaledText(
    text: String,
    x: Int, y: Int,
    scale: Float = 1f,
    color: Int = 0xFFFFFF,
    shadow: Boolean = true,
    align: TextAlign = TextAlign.LEFT
) {
    val tr = MinecraftClient.getInstance().textRenderer
    if (scale == 1f) {
        val tx = when (align) {
            TextAlign.CENTER -> x - tr.getWidth(text) / 2
            TextAlign.RIGHT  -> x - tr.getWidth(text)
            TextAlign.LEFT   -> x
        }
        if (shadow) drawTextWithShadow(tr, text, tx, y, color)
        else drawText(tr, text, tx, y, color, false)
        return
    }
    val m = matrices; m.push()
    m.translate(x.toFloat(), y.toFloat(), 0f)
    m.scale(scale, scale, 1f)
    val tx = when (align) {
        TextAlign.CENTER -> -(tr.getWidth(text) / 2)
        TextAlign.RIGHT  -> -tr.getWidth(text)
        TextAlign.LEFT   -> 0
    }
    if (shadow) drawTextWithShadow(tr, text, tx, 0, color)
    else drawText(tr, text, tx, 0, color, false)
    m.pop()
}

/** Рисует [Text] с масштабом и выравниванием. */
fun DrawContext.drawScaledText(
    text: Text,
    x: Int, y: Int,
    scale: Float = 1f,
    color: Int = 0xFFFFFF,
    shadow: Boolean = true,
    align: TextAlign = TextAlign.LEFT
) = drawScaledText(text.string, x, y, scale, color, shadow, align)

// ── Цвета ─────────────────────────────────────────────────────────────────

/** Применяет альфу к цвету (0..1). */
fun Int.withAlpha(alpha: Float): Int {
    val a = (alpha * 255).toInt().coerceIn(0, 255)
    return (this and 0x00FFFFFF) or (a shl 24)
}

/** Умножает существующую альфу цвета на множитель. */
fun Int.multiplyAlpha(factor: Float): Int {
    val existingAlpha = (this ushr 24) and 0xFF
    val newAlpha = (existingAlpha * factor).toInt().coerceIn(0, 255)
    return (this and 0x00FFFFFF) or (newAlpha shl 24)
}

/** Устанавливает альфу цвета (0..255). */
fun Int.withAlphaInt(alpha: Int): Int =
    (this and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)

// ── Layout хелперы ────────────────────────────────────────────────────────

object Layout {
    /**
     * X позиция начала ряда из [count] элементов шириной [itemW] с отступом [gap],
     * центрированного в контейнере шириной [containerW] начиная с [containerX].
     */
    fun centerRow(containerX: Int, containerW: Int, count: Int, itemW: Int, gap: Int = 0): Int =
        containerX + (containerW - count * itemW - (count - 1).coerceAtLeast(0) * gap) / 2

    /** X позиция i-го элемента в ряду. */
    fun rowX(startX: Int, index: Int, itemW: Int, gap: Int = 0): Int =
        startX + index * (itemW + gap)

    /** Y позиция i-го элемента в колонке. */
    fun colY(startY: Int, index: Int, itemH: Int, gap: Int = 0): Int =
        startY + index * (itemH + gap)

    /** Центр прямоугольника по X. */
    fun centerX(x: Int, w: Int) = x + w / 2

    /** Центр прямоугольника по Y. */
    fun centerY(y: Int, h: Int) = y + h / 2

    /** X для центрирования элемента шириной [itemW] в контейнере. */
    fun centerIn(containerX: Int, containerW: Int, itemW: Int): Int =
        containerX + (containerW - itemW) / 2

    /** Y для центрирования элемента высотой [itemH] в контейнере. */
    fun centerInY(containerY: Int, containerH: Int, itemH: Int): Int =
        containerY + (containerH - itemH) / 2
}
