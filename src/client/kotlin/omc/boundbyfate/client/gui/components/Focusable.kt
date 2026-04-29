package omc.boundbyfate.client.gui.components

/**
 * Компонент клавиатурного фокуса.
 *
 * Виджет с фокусом получает события клавиатуры.
 * Только один виджет может иметь фокус одновременно — управляется [FocusManager].
 *
 * ## Использование
 *
 * ```kotlin
 * class TextField : AnimOwner() {
 *     val focus = Focusable()
 *
 *     init {
 *         focus.onFocus { cursorVisible = true }
 *         focus.onBlur  { cursorVisible = false }
 *         focus.onKeyPress { key, mods ->
 *             if (key == GLFW.GLFW_KEY_ENTER) { submit(); true }
 *             else false
 *         }
 *         focus.onCharTyped { char -> text += char; true }
 *     }
 *
 *     fun handleClick(mouseX: Int, mouseY: Int, button: Int): Boolean {
 *         if (hover.isHovered) { FocusManager.requestFocus(focus); return true }
 *         return false
 *     }
 * }
 * ```
 */
class Focusable {
    val isFocused get() = FocusManager.current === this

    private val focusCallbacks   = mutableListOf<() -> Unit>()
    private val blurCallbacks    = mutableListOf<() -> Unit>()
    private val keyCallbacks     = mutableListOf<(key: Int, mods: Int) -> Boolean>()
    private val charCallbacks    = mutableListOf<(char: Char) -> Boolean>()

    fun onFocus(block: () -> Unit)                          { focusCallbacks += block }
    fun onBlur(block: () -> Unit)                           { blurCallbacks  += block }
    fun onKeyPress(block: (key: Int, mods: Int) -> Boolean) { keyCallbacks   += block }
    fun onCharTyped(block: (char: Char) -> Boolean)         { charCallbacks  += block }

    internal fun notifyFocus() = focusCallbacks.forEach { it() }
    internal fun notifyBlur()  = blurCallbacks.forEach  { it() }

    /** Обрабатывает нажатие клавиши. Возвращает true если обработано. */
    fun handleKeyPress(key: Int, mods: Int): Boolean {
        if (!isFocused) return false
        return keyCallbacks.any { it(key, mods) }
    }

    /** Обрабатывает ввод символа. Возвращает true если обработано. */
    fun handleCharTyped(char: Char): Boolean {
        if (!isFocused) return false
        return charCallbacks.any { it(char) }
    }
}

/**
 * Менеджер фокуса — только один виджет имеет фокус одновременно.
 */
object FocusManager {
    var current: Focusable? = null
        private set

    fun requestFocus(focusable: Focusable) {
        if (current === focusable) return
        current?.notifyBlur()
        current = focusable
        focusable.notifyFocus()
    }

    fun clearFocus() {
        current?.notifyBlur()
        current = null
    }

    fun handleKeyPress(key: Int, mods: Int): Boolean =
        current?.handleKeyPress(key, mods) ?: false

    fun handleCharTyped(char: Char): Boolean =
        current?.handleCharTyped(char) ?: false
}
