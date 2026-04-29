package omc.boundbyfate.client.gui.atlas

import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier
import omc.boundbyfate.client.gui.core.withAlpha

/**
 * Базовый класс типизированного атласа текстур.
 *
 * Все UV координаты определяются один раз — никаких магических чисел при рисовании.
 *
 * ## Создание атласа
 *
 * ```kotlin
 * object BbfGui : GuiAtlas("boundbyfate-core:textures/gui/atlas.png", 512, 512) {
 *     val PANEL      = nineSlice(0,   0,  64, 64, border = 8)
 *     val STAT_CARD  = nineSlice(64,  0,  48, 48, border = 6)
 *     val BTN_NORMAL = region(0, 128, 96, 20)
 *     val ICON_PROF  = region(473, 0, 14, 14)
 * }
 * ```
 *
 * ## Использование
 *
 * ```kotlin
 * BbfGui.PANEL.draw(context, x, y, width, height)
 * BbfGui.ICON_PROF.draw(context, x, y)
 * ```
 */
abstract class GuiAtlas(
    texturePath: String,
    val atlasWidth: Int,
    val atlasHeight: Int
) {
    val texture = Identifier(
        texturePath.substringBefore(':'),
        texturePath.substringAfter(':')
    )

    /** Создаёт обычный регион атласа. */
    fun region(u: Int, v: Int, w: Int, h: Int) =
        AtlasRegion(texture, u, v, w, h, atlasWidth, atlasHeight)

    /** Создаёт nine-slice регион — растягивается без искажений. */
    fun nineSlice(u: Int, v: Int, w: Int, h: Int, border: Int) =
        NineSliceRegion(texture, u, v, w, h, atlasWidth, atlasHeight, border)

    /** Создаёт регион с тремя состояниями для кнопки. */
    fun button(
        uNormal: Int, vNormal: Int,
        uHover: Int,  vHover: Int,
        uPressed: Int, vPressed: Int,
        w: Int, h: Int
    ) = ButtonRegion(
        texture,
        region(uNormal, vNormal, w, h),
        region(uHover,  vHover,  w, h),
        region(uPressed, vPressed, w, h)
    )

    /** Создаёт анимированный спрайт (несколько кадров в ряд). */
    fun sprite(u: Int, v: Int, frameW: Int, frameH: Int, frames: Int, fps: Float) =
        SpriteRegion(texture, u, v, frameW, frameH, frames, fps, atlasWidth, atlasHeight)
}

// ── Регионы ───────────────────────────────────────────────────────────────

/**
 * Обычный регион атласа.
 */
data class AtlasRegion(
    val texture: Identifier,
    val u: Int, val v: Int,
    val w: Int, val h: Int,
    val atlasW: Int, val atlasH: Int
) {
    /** Рисует регион с оригинальным размером. */
    fun draw(ctx: DrawContext, x: Int, y: Int) =
        draw(ctx, x, y, w, h)

    /** Рисует регион с заданным размером. */
    fun draw(ctx: DrawContext, x: Int, y: Int, drawW: Int, drawH: Int) {
        ctx.drawTexture(texture, x, y, drawW, drawH, u.toFloat(), v.toFloat(), w, h, atlasW, atlasH)
    }

    /** Рисует регион с альфой. */
    fun drawWithAlpha(ctx: DrawContext, x: Int, y: Int, alpha: Float, drawW: Int = w, drawH: Int = h) {
        if (alpha < 0.005f) return
        com.mojang.blaze3d.systems.RenderSystem.enableBlend()
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, alpha)
        draw(ctx, x, y, drawW, drawH)
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        com.mojang.blaze3d.systems.RenderSystem.disableBlend()
    }

    /** Рисует часть региона. */
    fun drawPartial(ctx: DrawContext, x: Int, y: Int, uOff: Int, vOff: Int, drawW: Int, drawH: Int) {
        ctx.drawTexture(texture, x, y, drawW, drawH,
            (u + uOff).toFloat(), (v + vOff).toFloat(), drawW, drawH, atlasW, atlasH)
    }
}

/**
 * Nine-slice регион — растягивается без искажений углов.
 *
 * Разбивает текстуру на 9 частей:
 * - 4 угла — не растягиваются
 * - 4 стороны — растягиваются в одном направлении
 * - центр — тайлится
 */
data class NineSliceRegion(
    val texture: Identifier,
    val u: Int, val v: Int,
    val w: Int, val h: Int,
    val atlasW: Int, val atlasH: Int,
    val border: Int
) {
    fun draw(ctx: DrawContext, x: Int, y: Int, drawW: Int, drawH: Int) {
        val b = border
        val innerW = drawW - b * 2
        val innerH = drawH - b * 2
        val srcInnerW = w - b * 2
        val srcInnerH = h - b * 2

        // Углы
        drawPart(ctx, x,              y,              b, b, u,          v)
        drawPart(ctx, x + drawW - b,  y,              b, b, u + w - b,  v)
        drawPart(ctx, x,              y + drawH - b,  b, b, u,          v + h - b)
        drawPart(ctx, x + drawW - b,  y + drawH - b,  b, b, u + w - b,  v + h - b)

        // Стороны
        drawPartStretched(ctx, x + b, y,             innerW, b,      u + b, v,          srcInnerW, b)
        drawPartStretched(ctx, x + b, y + drawH - b, innerW, b,      u + b, v + h - b,  srcInnerW, b)
        drawPartStretched(ctx, x,     y + b,          b,     innerH, u,     v + b,       b,         srcInnerH)
        drawPartStretched(ctx, x + drawW - b, y + b,  b,     innerH, u + w - b, v + b,  b,         srcInnerH)

        // Центр
        drawPartStretched(ctx, x + b, y + b, innerW, innerH, u + b, v + b, srcInnerW, srcInnerH)
    }

    private fun drawPart(ctx: DrawContext, x: Int, y: Int, dw: Int, dh: Int, su: Int, sv: Int) {
        ctx.drawTexture(texture, x, y, dw, dh, su.toFloat(), sv.toFloat(), dw, dh, atlasW, atlasH)
    }

    private fun drawPartStretched(ctx: DrawContext, x: Int, y: Int, dw: Int, dh: Int, su: Int, sv: Int, sw: Int, sh: Int) {
        if (dw <= 0 || dh <= 0) return
        ctx.drawTexture(texture, x, y, dw, dh, su.toFloat(), sv.toFloat(), sw, sh, atlasW, atlasH)
    }
}

/**
 * Кнопка с тремя состояниями.
 */
data class ButtonRegion(
    val texture: Identifier,
    val normal: AtlasRegion,
    val hovered: AtlasRegion,
    val pressed: AtlasRegion
) {
    enum class State { NORMAL, HOVERED, PRESSED, DISABLED }

    fun draw(ctx: DrawContext, x: Int, y: Int, drawW: Int, drawH: Int, state: State = State.NORMAL) {
        when (state) {
            State.NORMAL   -> normal.draw(ctx, x, y, drawW, drawH)
            State.HOVERED  -> hovered.draw(ctx, x, y, drawW, drawH)
            State.PRESSED  -> pressed.draw(ctx, x, y, drawW, drawH)
            State.DISABLED -> normal.drawWithAlpha(ctx, x, y, 0.5f, drawW, drawH)
        }
    }
}

/**
 * Анимированный спрайт — несколько кадров в ряд.
 */
class SpriteRegion(
    val texture: Identifier,
    val u: Int, val v: Int,
    val frameW: Int, val frameH: Int,
    val frames: Int,
    val fps: Float,
    val atlasW: Int, val atlasH: Int
) {
    private var time = 0f

    fun tick(delta: Float) {
        time += delta * fps / 20f  // delta в тиках, fps в кадрах/сек
    }

    fun draw(ctx: DrawContext, x: Int, y: Int, drawW: Int = frameW, drawH: Int = frameH) {
        val frame = time.toInt() % frames
        ctx.drawTexture(texture, x, y, drawW, drawH,
            (u + frame * frameW).toFloat(), v.toFloat(), frameW, frameH, atlasW, atlasH)
    }
}

// ── Наш основной атлас ────────────────────────────────────────────────────

/**
 * Основной атлас GUI BoundByFate.
 *
 * Все элементы интерфейса в одной текстуре.
 * Координаты обновляются при изменении atlas.png.
 */
object BbfGui : GuiAtlas("boundbyfate-core:textures/gui/atlas.png", 512, 512) {
    // Панели
    val PANEL_DARK   = nineSlice(0,   0,  64, 64, border = 8)
    val PANEL_LIGHT  = nineSlice(64,  0,  64, 64, border = 8)
    val DIALOG_BOX   = nineSlice(128, 0,  96, 80, border = 12)

    // Характеристики
    val STAT_CARD    = nineSlice(0,  64,  48, 48, border = 6)
    val STAT_CARD_HOVERED = nineSlice(48, 64, 48, 48, border = 6)

    // Баннеры
    val BANNER_LEFT  = region(0,   415, 66, 97)
    val BANNER_RIGHT = region(67,  415, 66, 97)
    val BANNER_TILE  = region(291, 459, 53, 53)
    val BANNER_HIGHLIGHT = region(138, 459, 152, 53)

    // Иконки
    val ICON_PROFICIENCY = region(473, 0, 14, 14)
    val ICON_SKILL_BG    = region(488, 0, 24, 24)
    val ICON_SAVE_BG     = region(445, 0, 27, 27)
    val ICON_HP_BG       = region(388, 197, 124, 136)

    // Кнопки
    val BTN_PRIMARY = button(
        uNormal = 0,  vNormal = 192,
        uHover  = 0,  vHover  = 212,
        uPressed = 0, vPressed = 232,
        w = 96, h = 20
    )
    val BTN_SECONDARY = button(
        uNormal = 96,  vNormal = 192,
        uHover  = 96,  vHover  = 212,
        uPressed = 96, vPressed = 232,
        w = 96, h = 20
    )

    // Скроллбар
    val SCROLL_TRACK = region(0, 256, 8, 64)
    val SCROLL_THUMB = region(8, 256, 8, 16)

    // Разделитель
    val DIVIDER_H = region(0, 320, 64, 2)
    val DIVIDER_V = region(64, 320, 2, 64)
}
