package omc.boundbyfate.client.keybind

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import org.lwjgl.glfw.GLFW

/**
 * Registers configurable keybindings for feature slots 1-9.
 *
 * Players can assign features to slots via the feature HUD (future implementation).
 * Default keys: R (slot 1), no default for slots 2-9.
 *
 * All keybindings are configurable in Minecraft's Controls settings
 * under the "BoundByFate" category.
 */
object FeatureKeyBindings {
    const val CATEGORY = "key.category.boundbyfate"
    const val SLOT_COUNT = 9

    val slots: Array<KeyBinding> = Array(SLOT_COUNT) { index ->
        val defaultKey = when (index) {
            0 -> GLFW.GLFW_KEY_R          // Slot 1: R
            1 -> GLFW.GLFW_KEY_F          // Slot 2: F
            2 -> GLFW.GLFW_KEY_G          // Slot 3: G
            else -> InputUtil.UNKNOWN_KEY.code  // Slots 4-9: unbound
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

    fun register() {
        // Registration happens in the array initializer above
        // This method exists for explicit call in client init
    }
}
