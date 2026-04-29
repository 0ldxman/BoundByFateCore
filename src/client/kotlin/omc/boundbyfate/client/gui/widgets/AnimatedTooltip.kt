package omc.boundbyfate.client.gui.widgets

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import omc.boundbyfate.client.gui.core.AnimOwner
import omc.boundbyfate.client.gui.core.RenderContext
import omc.boundbyfate.client.gui.core.TextAlign
import omc.boundbyfate.client.gui.core.drawScaledText
import omc.boundbyfate.client.gui.core.fillRectWithBorder
import omc.boundbyfate.client.gui.core.withAlpha

/**
 * Тултип с анимацией раскрытия — сначала по ширине, затем по высоте.
 *
 * ## Использование
 *
 * ```kotlin
 * class StatCard : AnimOwner() {
 *     val tooltip = AnimatedTooltip { Text.translatable("bbf.stat.str.tooltip") }
 *
 *     fun tick(ctx: RenderContext) {
 *         hover.update(ctx)
 *         tooltip.update(hover.isHovered, ctx.delta)
 *     }
 *
 *     fun render(ctx: RenderContext) {
 *         // основной рендер...
 *         tooltip.render(ctx.drawContext, hover.lastMouseX, hover.lastMouseY)
 *     }
 * }
 * ```
 */
class AnimatedTooltip(
    private val textProvider: () -> Text?
) : AnimOwner() {

    private val animW = animFloat(0f, speed = 0.2f)
    private val animH = animFloat(0f, speed = 0.15f)
    private var widthTimer = 0f
    private var lastText: String = ""

    /** Задержка перед появлением (в тиках). */
    var delay = 0f
    private var delayTimer = 0f

    fun update(isHovered: Boolean, delta: Float) {
        val text = if (isHovered) textProvider()?.string ?: "" else ""

        if (text != lastText) {
            // Новый текст — сбрасываем анимацию
            animW.snap(0f)
            animH.snap(0f)
            widthTimer = 0f
            delayTimer = 0f
            lastText = text
        }

        if (text.isNotEmpty()) {
            delayTimer += delta
            if (delayTimer >= delay) {
                animW.target = 1f
                if (animW.current > 0.85f) {
                    widthTimer += delta * 0.04f
                }
                if (widthTimer > 0.6f) {
                    animH.target = 1f
                }
            }
        } else {
            animW.target = 0f
            animH.target = 0f
            widthTimer = 0f
            delayTimer = 0f
        }

        tickAll(delta)
    }

    fun render(ctx: DrawContext, mouseX: Int, mouseY: Int) {
        if (animW.current < 0.01f) return
        val text = lastText.ifEmpty { return }

        val tr = MinecraftClient.getInstance().textRenderer
        val textScale = 0.75f
        val textW = (tr.getWidth(text) * textScale).toInt()
        val textH = (tr.fontHeight * textScale).toInt()
        val padX = 6; val padY = 4

        val fullW = textW + padX * 2
        val fullH = textH + padY * 2

        val drawW = (fullW * animW.current).toInt().coerceAtLeast(1)
        val drawH = (fullH * animH.current).toInt().coerceAtLeast(1)

        // Позиция — правее и ниже курсора, не выходит за экран
        val mc = MinecraftClient.getInstance()
        val x = (mouseX + 12).coerceAtMost(mc.window.scaledWidth - fullW - 4)
        val y = (mouseY + 4).coerceAtMost(mc.window.scaledHeight - fullH - 4)

        // Фон с анимацией раскрытия
        ctx.fillRectWithBorder(x, y, drawW, drawH,
            bg = 0xEE1a1a2e.toInt(),
            border = 0xFF6B4C9A.toInt()
        )

        // Текст появляется только когда высота почти полная
        if (animH.current > 0.7f) {
            val textAlpha = ((animH.current - 0.7f) / 0.3f).coerceIn(0f, 1f)
            ctx.drawScaledText(text, x + padX, y + padY,
                scale = textScale,
                color = 0xFFFFFF.withAlpha(textAlpha)
            )
        }
    }

    /** Рендер через RenderContext — тултип всегда в абсолютных координатах. */
    fun render(ctx: RenderContext) =
        render(ctx.drawContext, ctx.mouseX, ctx.mouseY)
}
