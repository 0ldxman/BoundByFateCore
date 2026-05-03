package omc.boundbyfate.client.gui.widgets

import net.minecraft.client.MinecraftClient
import omc.boundbyfate.client.gui.core.*

/**
 * Виджет текста с marquee-анимацией (горизонтальный скролл).
 *
 * Если текст влезает в контейнер — рисуется статично без анимации.
 * Если не влезает — запускается цикл:
 *   WAIT_START → SCROLLING → WAIT_END → FADE_RESET → WAIT_START → ...
 *
 * Скорость скролла зависит от длины переполнения:
 *   speed = (overflow / TARGET_SCROLL_DURATION).coerceIn(MIN_SPEED, MAX_SPEED)
 * Это значит короткое переполнение → медленный скролл, длинное → быстрее,
 * но не быстрее MAX_SPEED.
 *
 * ## Использование
 * ```kotlin
 * val title = MarqueeText("Ричард Зорге — великий разведчик")
 * // В layout:
 * add(title, width = 80, height = 10)
 * ```
 */
class MarqueeText(
    var text: String,
    var color: Int = -1,          // -1 = Theme.text.primary
    var scale: Float = 1f,
    var shadow: Boolean = false,
    var align: TextAlign = TextAlign.LEFT,

    /** Пауза перед началом скролла (сек). */
    var waitStart: Float = 1.5f,
    /** Пауза в конце скролла (сек). */
    var waitEnd: Float = 0.8f,
    /** Длительность fade-сброса (сек). */
    var fadeDuration: Float = 0.3f,

    /** Желаемое время прокрутки всего overflow (сек). Влияет на скорость. */
    var targetScrollDuration: Float = 3.0f,
    /** Минимальная скорость скролла (px/сек). */
    var minSpeed: Float = 8f,
    /** Максимальная скорость скролла (px/сек). */
    var maxSpeed: Float = 40f
) : BbfWidget() {

    // ── Фазы анимации ─────────────────────────────────────────────────────

    private enum class Phase { STATIC, WAIT_START, SCROLLING, WAIT_END, FADE_RESET }

    private var phase = Phase.STATIC
    private var timer = 0f       // таймер текущей фазы (сек)
    private var scrollX = 0f     // текущее смещение текста (px)
    private var alpha = 1f       // текущая прозрачность

    // Кэшированные значения — пересчитываются при изменении текста или ширины
    private var lastText = ""
    private var lastCtxWidth = -1
    private var textWidth = 0
    private var overflow = 0     // textWidth - ctxWidth (в px)
    private var speed = 0f       // вычисленная скорость (px/сек)

    // ── Tick ──────────────────────────────────────────────────────────────

    override fun tick(ctx: RenderContext) {
        val tr = MinecraftClient.getInstance().textRenderer

        // Пересчитываем если изменился текст или ширина контейнера
        if (text != lastText || ctx.width != lastCtxWidth) {
            lastText = text
            lastCtxWidth = ctx.width
            textWidth = (tr.getWidth(text) * scale).toInt()
            overflow = textWidth - ctx.width

            if (overflow <= 0) {
                // Текст влезает — статичный режим
                phase = Phase.STATIC
                scrollX = 0f
                alpha = 1f
            } else {
                // Вычисляем скорость от длины overflow
                speed = (overflow / targetScrollDuration).coerceIn(minSpeed, maxSpeed)
                if (phase == Phase.STATIC) {
                    phase = Phase.WAIT_START
                    timer = 0f
                }
            }
        }

        if (phase == Phase.STATIC) {
            tickAll(ctx.delta)
            return
        }

        timer += ctx.delta

        when (phase) {
            Phase.WAIT_START -> {
                if (timer >= waitStart) {
                    phase = Phase.SCROLLING
                    timer = 0f
                }
            }
            Phase.SCROLLING -> {
                scrollX += speed * ctx.delta
                if (scrollX >= overflow) {
                    scrollX = overflow.toFloat()
                    phase = Phase.WAIT_END
                    timer = 0f
                }
            }
            Phase.WAIT_END -> {
                if (timer >= waitEnd) {
                    phase = Phase.FADE_RESET
                    timer = 0f
                }
            }
            Phase.FADE_RESET -> {
                val halfFade = fadeDuration / 2f
                alpha = if (timer < halfFade) {
                    // Fade out
                    1f - (timer / halfFade)
                } else {
                    // Сброс в середине fade
                    if (scrollX != 0f) {
                        scrollX = 0f
                    }
                    // Fade in
                    (timer - halfFade) / halfFade
                }
                alpha = alpha.coerceIn(0f, 1f)

                if (timer >= fadeDuration) {
                    alpha = 1f
                    phase = Phase.WAIT_START
                    timer = 0f
                }
            }
            Phase.STATIC -> { /* handled above */ }
        }

        tickAll(ctx.delta)
    }

    // ── Render ────────────────────────────────────────────────────────────

    override fun render(ctx: RenderContext) {
        val resolvedColor = (if (color == -1) Theme.text.primary else color).withAlpha(alpha)

        if (phase == Phase.STATIC) {
            // Статичный текст — обычный рендер с выравниванием
            val x = when (align) {
                TextAlign.LEFT   -> ctx.x
                TextAlign.CENTER -> ctx.cx
                TextAlign.RIGHT  -> ctx.right
            }
            val y = ctx.cy - ((8f * scale) / 2f).toInt()
            ctx.drawContext.drawScaledText(text, x, y, scale = scale, color = resolvedColor, align = align, shadow = shadow)
            return
        }

        // Анимированный текст — обрезаем по ширине и смещаем
        val y = ctx.cy - ((8f * scale) / 2f).toInt()
        ctx.drawContext.withClip(ctx.x, ctx.y, ctx.width, ctx.height) {
            drawScaledText(
                text,
                x = ctx.x - scrollX.toInt(),
                y = y,
                scale = scale,
                color = resolvedColor,
                align = TextAlign.LEFT,
                shadow = shadow
            )
        }
    }
}
