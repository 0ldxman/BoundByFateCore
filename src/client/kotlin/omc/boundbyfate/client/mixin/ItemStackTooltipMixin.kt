package omc.boundbyfate.client.mixin

import net.minecraft.client.item.TooltipContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import omc.boundbyfate.registry.BbfItemTags
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(value = [ItemStack::class])
abstract class ItemStackTooltipMixin {

    @Inject(method = ["getTooltip"], at = [At("RETURN")])
    private fun bbf_addProficiencyTooltip(
        player: PlayerEntity?,
        context: TooltipContext,
        ci: CallbackInfoReturnable<MutableList<Text>>
    ) {
        val stack = this as ItemStack
        if (stack.isEmpty) return

        val categories = BbfItemTags.ALL
            .filter { (_, tag) -> stack.isIn(tag) }
            .map { (name, _) -> name }

        if (categories.isEmpty()) return

        val tooltip = ci.returnValue
        tooltip.add(Text.empty())
        tooltip.add(
            Text.literal("Владение: ").formatted(Formatting.GRAY)
                .append(Text.literal(categories.joinToString(", ")).formatted(Formatting.YELLOW))
        )
    }
}
