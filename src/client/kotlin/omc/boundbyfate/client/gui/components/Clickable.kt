package omc.boundbyfate.client.gui.components

import omc.boundbyfate.client.gui.core.UiSounds
import omc.boundbyfate.client.gui.core.playUi

/**
 * Компонент обработки кликов.
 *
 * ## Использование
 *
 * ```kotlin
 * class MyWidget : AnimOwner() {
 *     val click = Clickable()
 *
 *     init {
 *         click.onClick { doSomething() }
 *     }
 *
 *     fun handleClick(mouseX: Int, mouseY: Int, button: Int): Boolean =
 *         click.handle(mouseX, mouseY, button, hover.isHovered)
 * }
 * ```
 */
class Clickable(
    var playSoundOnClick: Boolean = true,
    var enabled: () -> Boolean = { true }
) {
    private val clickCallbacks        = mutableListOf<() -> Unit>()
    private val pressCallbacks        = mutableListOf<() -> Unit>()
    private val releaseCallbacks      = mutableListOf<() -> Unit>()
    private val doubleClickCallbacks  = mutableListOf<() -> Unit>()
    private val rightClickCallbacks   = mutableListOf<(mouseX: Int, mouseY: Int) -> Unit>()

    private var lastClickTime = 0L
    private val doubleClickThresholdMs = 300L

    fun onClick(block: () -> Unit)                              { clickCallbacks       += block }
    fun onPress(block: () -> Unit)                              { pressCallbacks       += block }
    fun onRelease(block: () -> Unit)                            { releaseCallbacks     += block }
    fun onDoubleClick(block: () -> Unit)                        { doubleClickCallbacks += block }
    fun onRightClick(block: (mouseX: Int, mouseY: Int) -> Unit) { rightClickCallbacks  += block }

    /**
     * Обрабатывает клик. Возвращает true если событие было обработано.
     *
     * @param isHovered находится ли курсор над виджетом
     */
    fun handle(mouseX: Int, mouseY: Int, button: Int, isHovered: Boolean): Boolean {
        if (!isHovered || !enabled()) return false

        when (button) {
            0 -> { // ЛКМ
                pressCallbacks.forEach { it() }

                val now = System.currentTimeMillis()
                val isDouble = (now - lastClickTime) < doubleClickThresholdMs
                lastClickTime = now

                if (isDouble) {
                    doubleClickCallbacks.forEach { it() }
                } else {
                    if (playSoundOnClick) UiSounds.current.click?.playUi()
                    clickCallbacks.forEach { it() }
                }
                return true
            }
            1 -> { // ПКМ
                rightClickCallbacks.forEach { it(mouseX, mouseY) }
                return rightClickCallbacks.isNotEmpty()
            }
        }
        return false
    }

    fun handleRelease(button: Int, isHovered: Boolean): Boolean {
        if (!isHovered || button != 0) return false
        releaseCallbacks.forEach { it() }
        return releaseCallbacks.isNotEmpty()
    }
}
