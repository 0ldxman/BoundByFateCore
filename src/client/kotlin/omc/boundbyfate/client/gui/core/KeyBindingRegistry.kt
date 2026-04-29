package omc.boundbyfate.client.gui.core

import org.lwjgl.glfw.GLFW

/**
 * Декларативный реестр горячих клавиш для экрана.
 *
 * ## Использование
 *
 * ```kotlin
 * class MyScreen : BbfScreen() {
 *     override fun init() {
 *         super.init()
 *         keys.bind(GLFW.GLFW_KEY_ESCAPE)          { close() }
 *         keys.bind(GLFW.GLFW_KEY_TAB)             { nextTab() }
 *         keys.bind(GLFW.GLFW_KEY_E)               { openInventory() }
 *         keys.bind(GLFW.GLFW_KEY_S, mods = CTRL)  { save() }
 *     }
 * }
 * ```
 */
class KeyBindingRegistry {

    companion object {
        const val CTRL  = GLFW.GLFW_MOD_CONTROL
        const val SHIFT = GLFW.GLFW_MOD_SHIFT
        const val ALT   = GLFW.GLFW_MOD_ALT
    }

    private data class Binding(
        val key: Int,
        val mods: Int,
        val description: String,
        val action: () -> Unit
    ) {
        fun matches(key: Int, mods: Int) =
            this.key == key && (this.mods == 0 || (mods and this.mods) == this.mods)
    }

    private val bindings = mutableListOf<Binding>()

    /**
     * Регистрирует горячую клавишу.
     *
     * @param key GLFW код клавиши
     * @param mods модификаторы (CTRL, SHIFT, ALT или их комбинации через or)
     * @param description описание для подсказки (опционально)
     * @param action действие при нажатии
     */
    fun bind(
        key: Int,
        mods: Int = 0,
        description: String = "",
        action: () -> Unit
    ) {
        bindings += Binding(key, mods, description, action)
    }

    /**
     * Обрабатывает нажатие клавиши.
     * @return true если клавиша была обработана
     */
    fun handle(key: Int, mods: Int): Boolean {
        val binding = bindings.firstOrNull { it.matches(key, mods) } ?: return false
        binding.action()
        return true
    }

    /**
     * Возвращает список подсказок для отображения в UI.
     * Только биндинги с непустым description.
     */
    fun getHints(): List<Pair<String, String>> =
        bindings
            .filter { it.description.isNotEmpty() }
            .map { keyName(it.key, it.mods) to it.description }

    fun clear() = bindings.clear()

    private fun keyName(key: Int, mods: Int): String {
        val prefix = buildString {
            if (mods and CTRL  != 0) append("Ctrl+")
            if (mods and SHIFT != 0) append("Shift+")
            if (mods and ALT   != 0) append("Alt+")
        }
        val name = when (key) {
            GLFW.GLFW_KEY_ESCAPE    -> "Esc"
            GLFW.GLFW_KEY_ENTER     -> "Enter"
            GLFW.GLFW_KEY_TAB       -> "Tab"
            GLFW.GLFW_KEY_BACKSPACE -> "Backspace"
            GLFW.GLFW_KEY_DELETE    -> "Del"
            GLFW.GLFW_KEY_UP        -> "↑"
            GLFW.GLFW_KEY_DOWN      -> "↓"
            GLFW.GLFW_KEY_LEFT      -> "←"
            GLFW.GLFW_KEY_RIGHT     -> "→"
            GLFW.GLFW_KEY_SPACE     -> "Space"
            in GLFW.GLFW_KEY_A..GLFW.GLFW_KEY_Z -> ('A' + (key - GLFW.GLFW_KEY_A)).toString()
            in GLFW.GLFW_KEY_0..GLFW.GLFW_KEY_9 -> ('0' + (key - GLFW.GLFW_KEY_0)).toString()
            in GLFW.GLFW_KEY_F1..GLFW.GLFW_KEY_F12 -> "F${key - GLFW.GLFW_KEY_F1 + 1}"
            else -> "[$key]"
        }
        return "$prefix$name"
    }
}
