package omc.boundbyfate.client.mixin

import net.minecraft.client.MinecraftClient
import net.minecraft.client.item.TooltipContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import omc.boundbyfate.client.tooltip.ItemTooltipManager
import org.lwjgl.glfw.GLFW
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(value = [ItemStack::class])
abstract class ItemStackTooltipMixin {

    @Inject(method = ["getTooltip"], at = [At("RETURN")])
    private fun bbf_appendTooltip(
        player: PlayerEntity?,
        context: TooltipContext,
        ci: CallbackInfoReturnable<MutableList<Text>>
    ) {
        val stack = this as ItemStack
        if (stack.isEmpty) return

        val window = MinecraftClient.getInstance().window?.handle ?: return
        val shiftHeld = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                        GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS

        ItemTooltipManager.appendToTooltip(stack, ci.returnValue, shiftHeld)
    }
}
