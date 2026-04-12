package omc.boundbyfate.client.tooltip

import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * Central manager for BbF item tooltip sections.
 *
 * Collects sections from all registered [BbfTooltipProvider]s and renders them.
 * When SHIFT is not held, shows only a "SHIFT для подробностей" hint if there's content.
 * When SHIFT is held, renders all sections in full.
 *
 * Usage:
 * ```kotlin
 * // Register a provider (do this in client init)
 * ItemTooltipManager.register(ProficiencyTooltipProvider)
 *
 * // In mixin, call:
 * ItemTooltipManager.appendToTooltip(stack, tooltip, shiftHeld)
 * ```
 */
object ItemTooltipManager {
    private val providers = mutableListOf<BbfTooltipProvider>()

    fun register(provider: BbfTooltipProvider) {
        providers.add(provider)
    }

    /**
     * Appends BbF tooltip content to the given tooltip list.
     * Call this from the ItemStack.getTooltip mixin.
     */
    fun appendToTooltip(stack: ItemStack, tooltip: MutableList<Text>, shiftHeld: Boolean) {
        val allSections = providers.flatMap { it.getSections(stack, shiftHeld) }
        if (allSections.isEmpty()) return

        tooltip.add(Text.empty())

        if (!shiftHeld) {
            tooltip.add(
                Text.literal("SHIFT")
                    .formatted(Formatting.DARK_GRAY)
                    .append(Text.literal(" для подробностей").formatted(Formatting.DARK_GRAY))
            )
            return
        }

        // SHIFT held — render all sections
        allSections.forEach { section ->
            section.render().forEach { line -> tooltip.add(line) }
        }
    }
}
