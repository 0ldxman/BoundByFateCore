package omc.boundbyfate.client.tooltip

import net.minecraft.item.ItemStack

/**
 * Interface for anything that wants to contribute BbF tooltip sections.
 *
 * Implement this and register via [ItemTooltipManager.register] to add
 * custom sections to item tooltips.
 *
 * Example: proficiency provider, lore provider, damage type provider, etc.
 */
fun interface BbfTooltipProvider {
    /**
     * Returns tooltip sections for the given item stack.
     * Return empty list if this provider has nothing to show for this item.
     *
     * @param stack The item being hovered
     * @param shiftHeld Whether the player is holding SHIFT
     */
    fun getSections(stack: ItemStack, shiftHeld: Boolean): List<BbfTooltipSection>
}
