package omc.boundbyfate.client.gui.core

import net.minecraft.client.gui.DrawContext
import omc.boundbyfate.client.gui.components.FocusManager

/**
 * Стек оверлеев — управляет вложенными панелями поверх основного экрана.
 *
 * Оверлеи рендерятся в порядке стека (нижний первым).
 * Клики и клавиши получает только верхний оверлей.
 * При закрытии верхнего — нижний получает [Overlay.onResume].
 *
 * ## Использование
 *
 * ```kotlin
 * // Открыть оверлей
 * overlays.push(MotivationsOverlay(motivations))
 *
 * // Закрыть верхний
 * overlays.pop()
 *
 * // Закрыть все
 * overlays.clear()
 * ```
 */
class OverlayStack {
    private val stack = mutableListOf<Overlay>()

    val isEmpty get() = stack.isEmpty()
    val top get() = stack.lastOrNull()
    val size get() = stack.size

    /** Открывает оверлей поверх текущего. */
    fun push(overlay: Overlay) {
        stack.lastOrNull()?.onPause()
        stack += overlay
        overlay.onOpen()
    }

    /** Закрывает верхний оверлей с анимацией. */
    fun pop() {
        val top = stack.lastOrNull() ?: return
        top.onClose()
        top.onAnimationFinished {
            stack.remove(top)
            stack.lastOrNull()?.onResume()
        }
    }

    /** Закрывает все оверлеи. */
    fun clear() {
        while (stack.isNotEmpty()) pop()
    }

    fun tick(delta: Float) = stack.forEach { it.tick(delta) }

    fun render(ctx: DrawContext, mouseX: Int, mouseY: Int) =
        stack.forEach { it.render(ctx, mouseX, mouseY) }

    /** Передаёт клик верхнему оверлею. */
    fun handleClick(mouseX: Int, mouseY: Int, button: Int): Boolean {
        val top = stack.lastOrNull() ?: return false
        val result = top.handleClick(mouseX, mouseY, button)
        // Если оверлей вернул true как сигнал "закрыть" — закрываем
        if (result && top.closeOnClickOutside && !top.isInsideBounds(mouseX, mouseY)) {
            pop()
        }
        return stack.isNotEmpty()  // блокируем клики для основного экрана пока есть оверлеи
    }

    fun handleRelease(mouseX: Int, mouseY: Int, button: Int): Boolean {
        stack.lastOrNull()?.handleRelease(mouseX, mouseY, button)
        return stack.isNotEmpty()
    }

    fun handleScroll(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        stack.lastOrNull()?.handleScroll(mouseX, mouseY, amount)
        return stack.isNotEmpty()
    }

    fun handleKey(key: Int, mods: Int): Boolean {
        val top = stack.lastOrNull() ?: return false
        val handled = top.handleKey(key, mods)
        if (handled && top.shouldClose) pop()
        return handled || stack.isNotEmpty()
    }

    fun handleChar(char: Char): Boolean {
        stack.lastOrNull()?.handleChar(char)
        return stack.isNotEmpty()
    }
}

/**
 * Базовый класс оверлея.
 *
 * Оверлей — самостоятельный объект со своим жизненным циклом.
 * Не флаги в родительском экране.
 *
 * ## Создание оверлея
 *
 * ```kotlin
 * class MotivationsOverlay(val motivations: List<Motivation>) : Overlay() {
 *     override fun render(ctx: DrawContext, mouseX: Int, mouseY: Int) {
 *         dimBackground()
 *         ctx.transform(alpha = openProgress.current) {
 *             // содержимое
 *         }
 *     }
 *
 *     override fun handleKey(key: Int, mods: Int): Boolean {
 *         if (key == GLFW.GLFW_KEY_ESCAPE) { shouldClose = true; return true }
 *         return false
 *     }
 * }
 * ```
 */
abstract class Overlay : AnimOwner() {

    /** Прогресс анимации открытия/закрытия (0..1). */
    val openProgress = animFloat(0f, speed = 0.1f)

    private var isClosing = false
    private val finishedCallbacks = mutableListOf<() -> Unit>()

    /** Если true — оверлей закроется после обработки текущего события. */
    var shouldClose = false

    /** Закрывать ли при клике вне границ. */
    open val closeOnClickOutside = true

    open fun onOpen() {
        openProgress.snap(0f)
        openProgress.target = 1f
        UiSounds.current.overlayOpen?.playUi()
    }

    open fun onClose() {
        isClosing = true
        openProgress.target = 0f
        UiSounds.current.overlayClose?.playUi()
        FocusManager.clearFocus()
    }

    open fun onPause()  {}
    open fun onResume() {}

    fun onAnimationFinished(block: () -> Unit) { finishedCallbacks += block }

    open fun tick(delta: Float) {
        tickAll(delta)
        if (isClosing && openProgress.current < 0.005f) {
            finishedCallbacks.forEach { it() }
            finishedCallbacks.clear()
            isClosing = false
        }
    }

    abstract fun render(ctx: DrawContext, mouseX: Int, mouseY: Int)

    open fun handleClick(mouseX: Int, mouseY: Int, button: Int): Boolean = false
    open fun handleRelease(mouseX: Int, mouseY: Int, button: Int) {}
    open fun handleScroll(mouseX: Double, mouseY: Double, amount: Double) {}
    open fun handleKey(key: Int, mods: Int): Boolean = false
    open fun handleChar(char: Char) {}

    /** Проверяет находится ли точка внутри границ оверлея. */
    open fun isInsideBounds(mouseX: Int, mouseY: Int): Boolean = true

    /**
     * Затемняет весь экран под оверлеем.
     * Вызывай в начале [render].
     */
    protected fun DrawContext.dimBackground(alpha: Float = 0.6f) {
        val mc = net.minecraft.client.MinecraftClient.getInstance()
        fill(0, 0, mc.window.scaledWidth, mc.window.scaledHeight,
            0x000000.withAlpha(alpha * openProgress.current))
    }
}
