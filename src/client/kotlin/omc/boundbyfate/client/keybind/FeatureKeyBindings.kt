package omc.boundbyfate.client.keybind

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import org.lwjgl.glfw.GLFW

/**
 * Registers configurable keybindings for feature slots 1-10.
 * Default: CTRL+1 through CTRL+0
 * All configurable in Controls settings under "BoundByFate" category.
 */
object FeatureKeyBindings {
    const val CATEGORY = "key.category.boundbyfate"
    const val SLOT_COUNT = 10

    val slots: Array<KeyBinding> = Array(SLOT_COUNT) { index ->
        val defaultKey = when (index) {
            0 -> GLFW.GLFW_KEY_1
            1 -> GLFW.GLFW_KEY_2
            2 -> GLFW.GLFW_KEY_3
            3 -> GLFW.GLFW_KEY_4
            4 -> GLFW.GLFW_KEY_5
            5 -> GLFW.GLFW_KEY_6
            6 -> GLFW.GLFW_KEY_7
            7 -> GLFW.GLFW_KEY_8
            8 -> GLFW.GLFW_KEY_9
            9 -> GLFW.GLFW_KEY_0
            else -> InputUtil.UNKNOWN_KEY.code
        }

        KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.boundbyfate.feature_slot_${index + 1}",
                InputUtil.Type.KEYSYM,
                defaultKey,
                CATEGORY
            )
        )
    }

    /** Open feature screen */
    val openFeatureScreen: KeyBinding = KeyBindingHelper.registerKeyBinding(
        KeyBinding("key.boundbyfate.open_features", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY)
    )

    /** Open character sheet */
    val openCharacterSheet: KeyBinding = KeyBindingHelper.registerKeyBinding(
        KeyBinding("key.boundbyfate.open_character", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY)
    )

    fun register() { /* initialization happens in array/val declarations */ }
}
