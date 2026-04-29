package omc.boundbyfate.client.keybind

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import org.lwjgl.glfw.GLFW

/**
 * Реестр кейбиндингов мода BoundByFate.
 *
 * Добавляй кейбиндинги сюда по мере необходимости через [register].
 * Игрок может переназначить любой в меню Управление → BoundByFate.
 *
 * ## Добавление нового кейбиндинга
 *
 * ```kotlin
 * val MY_KEY = BbfKeybinds.register("my_action", GLFW.GLFW_KEY_M)
 * ```
 *
 * ## Использование
 *
 * ```kotlin
 * // Один раз при нажатии (для toggle)
 * while (BbfKeybinds.MY_KEY.wasPressed()) { doSomething() }
 *
 * // Пока зажато (для HUD visibleWhen)
 * visibleWhen { BbfKeybinds.MY_KEY.isPressed }
 * ```
 */
object BbfKeybinds {

    const val CATEGORY = "key.category.boundbyfate"

    // ── Кейбиндинги ───────────────────────────────────────────────────────
    // Добавляй сюда по мере необходимости.

    /** Переключить расширенный HUD. */
    val TOGGLE_DETAILED_HUD = register("toggle_detailed_hud", GLFW.GLFW_KEY_UNKNOWN)

    // ── Утилиты ───────────────────────────────────────────────────────────

    /**
     * Регистрирует новый кейбиндинг.
     *
     * @param name уникальное имя (ключ локализации: `key.boundbyfate.<name>`)
     * @param defaultKey GLFW код клавиши по умолчанию
     *                   ([GLFW.GLFW_KEY_UNKNOWN] = не назначена)
     */
    fun register(name: String, defaultKey: Int): KeyBinding =
        KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.boundbyfate.$name",
                InputUtil.Type.KEYSYM,
                defaultKey,
                CATEGORY
            )
        )
}
