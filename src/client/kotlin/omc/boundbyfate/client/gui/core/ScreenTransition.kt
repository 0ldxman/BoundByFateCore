package omc.boundbyfate.client.gui.core

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen

/**
 * Переходы между экранами с анимацией.
 *
 * ## Использование
 *
 * ```kotlin
 * // Простой переход
 * ScreenTransition.open(CharacterSelectScreen())
 *
 * // С конкретным типом перехода
 * ScreenTransition.open(StatsScreen(), ScreenTransition.Type.SLIDE_LEFT)
 * ```
 */
object ScreenTransition {

    enum class Type {
        NONE,       // мгновенно
        FADE,       // затухание
        SLIDE_LEFT, // выезжает слева
        SLIDE_UP,   // выезжает снизу
        ZOOM_IN     // появляется из центра
    }

    private var pendingScreen: Screen? = null
    private var transitionType = Type.NONE
    private var progress = 0f
    private var isTransitioning = false
    private var phase = Phase.NONE

    private enum class Phase { NONE, FADE_OUT, FADE_IN }

    /**
     * Открывает экран с анимацией перехода.
     */
    fun open(screen: Screen, type: Type = Type.FADE) {
        if (type == Type.NONE) {
            MinecraftClient.getInstance().setScreen(screen)
            return
        }
        pendingScreen = screen
        transitionType = type
        phase = Phase.FADE_OUT
        progress = 0f
        isTransitioning = true
    }

    /**
     * Закрывает текущий экран с анимацией.
     */
    fun close(type: Type = Type.FADE) {
        open(null as Screen? ?: return, type)
    }

    /**
     * Тикает переход. Вызывается из рендер цикла.
     * @return true если нужно рисовать оверлей перехода
     */
    fun tick(delta: Float): Boolean {
        if (!isTransitioning) return false

        progress += delta * 0.08f

        when (phase) {
            Phase.FADE_OUT -> {
                if (progress >= 1f) {
                    MinecraftClient.getInstance().setScreen(pendingScreen)
                    pendingScreen = null
                    phase = Phase.FADE_IN
                    progress = 0f
                }
            }
            Phase.FADE_IN -> {
                if (progress >= 1f) {
                    isTransitioning = false
                    phase = Phase.NONE
                }
            }
            Phase.NONE -> {}
        }

        return true
    }

    /**
     * Рисует оверлей перехода поверх всего.
     */
    fun render(ctx: net.minecraft.client.gui.DrawContext) {
        if (!isTransitioning) return

        val mc = MinecraftClient.getInstance()
        val w = mc.window.scaledWidth
        val h = mc.window.scaledHeight

        val alpha = when (phase) {
            Phase.FADE_OUT -> Easing.EASE_IN(progress.coerceIn(0f, 1f))
            Phase.FADE_IN  -> 1f - Easing.EASE_OUT(progress.coerceIn(0f, 1f))
            Phase.NONE     -> 0f
        }

        if (alpha > 0.005f) {
            ctx.fill(0, 0, w, h, 0x000000.withAlpha(alpha))
        }
    }
}
