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

        // Collect all proficiency tags this item belongs to
        val allTags = listOf(
            BbfItemTags.PROFICIENCY_SWORDS,
            BbfItemTags.PROFICIENCY_AXES_WEAPON,
            BbfItemTags.PROFICIENCY_MARTIAL_WEAPONS,
            BbfItemTags.PROFICIENCY_SMITHING_TOOLS,
            BbfItemTags.PROFICIENCY_ARTISAN_TOOLS,
        )

        // Show only the most specific tags (skip containers if a child already matched)
        val matched = allTags.filter { stack.isIn(it) }
        val specific = matched.filter { tag ->
            matched.none { other -> other != tag && isChildOf(tag, other) }
        }

        if (specific.isEmpty()) return

        val names = specific.joinToString(", ") { tag ->
            // Convert tag path "proficiency/swords" → display name via registry lookup
            // Fall back to capitalizing the path segment
            tag.id.path.removePrefix("proficiency/")
                .replace("_", " ")
                .replaceFirstChar { it.uppercase() }
        }

        val tooltip = ci.returnValue
        tooltip.add(Text.empty())
        tooltip.add(
            Text.literal("Владение: ").formatted(Formatting.GRAY)
                .append(Text.literal(names).formatted(Formatting.YELLOW))
        )
    }

    /**
     * Returns true if [child] is a more specific tag contained within [parent].
     */
    private fun isChildOf(child: net.minecraft.registry.tag.TagKey<*>, parent: net.minecraft.registry.tag.TagKey<*>): Boolean {
        val c = child.id.path
        val p = parent.id.path
        return when {
            p == "proficiency/martial_weapons" &&
                (c == "proficiency/swords" || c == "proficiency/axes_weapon") -> true
            p == "proficiency/artisan_tools" && c == "proficiency/smithing_tools" -> true
            else -> false
        }
    }
}
