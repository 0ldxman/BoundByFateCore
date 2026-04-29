package omc.boundbyfate.client.gui.core

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import omc.boundbyfate.client.gui.components.DragDropManager
import omc.boundbyfate.client.gui.components.FocusManager

/**
 * Базовый класс для всех экранов BoundByFate.
 *
 * Управляет:
 * - [OverlayStack] — стек оверлеев любой глубины
 * - [KeyBindingRegistry] — декларативные горячие клавиши
 * - [UiSounds] — звуковая тема экрана
 * - [LayeredRenderer] — Z-слои рендера
 *
 * ## Создание экрана
 *
 * ```kotlin
 * class CharacterScreen : BbfScreen("screen.bbf.character") {
 *     private val openAnim = AnimSequence()
 *         .then(0.3f) { p -> titleAlpha.target = p }
 *         .then(0.4f) { p -> cardsProgress = p }
 *
 *     override fun init() {
 *         super.init()
 *         openAnim.reset()
 *         keys.bind(GLFW.GLFW_KEY_ESCAPE) { close() }
 *     }
 *
 *     override fun renderContent(ctx: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
 *         openAnim.tick(delta)
 *         // рисуем содержимое
 *     }
 * }
 * ```
 */
abstract class BbfScreen(
    titleKey: String = "screen.bbf.default"
) : Screen(Text.translatable(titleKey)) {

    /** Стек оверлеев. */
    protected val overlays = OverlayStack()

    /** Реестр горячих клавиш. */
    protected val keys = KeyBindingRegistry()

    /** Слоёный рендерер для правильного Z-порядка. */
    protected val layered = LayeredRenderer()

    /** Звуковая тема. Переопредели для кастомного звука. */
    open val soundTheme: UiSoundTheme = UiSounds.DEFAULT

    override fun init() {
        super.init()
        keys.clear()
        UiSounds.current = soundTheme
        soundTheme.open?.playUi()
        onInit()
    }

    /** Вызывается при инициализации. Переопредели вместо [init]. */
    open fun onInit() {}

    override fun render(ctx: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(ctx)
        renderContent(ctx, mouseX, mouseY, delta)

        // Оверлеи поверх основного контента
        overlays.tick(delta)
        overlays.render(ctx, mouseX, mouseY)

        // Слоёный рендер (тултипы, попапы)
        layered.flush(ctx)

        super.render(ctx, mouseX, mouseY, delta)
    }

    /** Основной контент экрана. Реализуй этот метод. */
    abstract fun renderContent(ctx: DrawContext, mouseX: Int, mouseY: Int, delta: Float)

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt(); val my = mouseY.toInt()
        if (overlays.handleClick(mx, my, button)) return true
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt(); val my = mouseY.toInt()
        if (overlays.handleRelease(mx, my, button)) return true
        DragDropManager.currentSource?.handleRelease(dropped = false)
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        DragDropManager.dragX = mouseX.toInt()
        DragDropManager.dragY = mouseY.toInt()
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (overlays.handleScroll(mouseX, mouseY, amount)) return true
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (overlays.handleKey(keyCode, modifiers)) return true
        if (FocusManager.handleKeyPress(keyCode, modifiers)) return true
        if (keys.handle(keyCode, modifiers)) return true
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (overlays.handleChar(chr)) return true
        if (FocusManager.handleCharTyped(chr)) return true
        return super.charTyped(chr, modifiers)
    }

    override fun removed() {
        soundTheme.close?.playUi()
        FocusManager.clearFocus()
        UiSounds.current = UiSounds.DEFAULT
        super.removed()
    }

    override fun shouldPause() = false

    /** Закрывает экран. */
    override fun close() {
        MinecraftClient.getInstance().setScreen(null)
    }

    /** Открывает экран. */
    fun open() {
        MinecraftClient.getInstance().setScreen(this)
    }
}

/**
 * Слоёный рендерер — гарантирует правильный Z-порядок.
 *
 * Тултипы всегда поверх попапов, попапы поверх контента.
 */
class LayeredRenderer {
    enum class Layer { CONTENT, OVERLAY, POPUP, TOOLTIP }

    private val queues = Layer.entries.associateWith { mutableListOf<(DrawContext) -> Unit>() }

    fun enqueue(layer: Layer, block: (DrawContext) -> Unit) {
        queues[layer]!! += block
    }

    /** Рисует все слои в правильном порядке и очищает очереди. */
    fun flush(ctx: DrawContext) {
        Layer.entries.forEach { layer ->
            queues[layer]!!.forEach { it(ctx) }
            queues[layer]!!.clear()
        }
    }
}
