package omc.boundbyfate.client.tooltip

import net.minecraft.item.ItemStack
import omc.boundbyfate.registry.BbfItemTags

/**
 * Tooltip provider that shows proficiency categories for an item.
 *
 * Shows which proficiency categories the item belongs to (e.g. "Мечи", "Воинское оружие").
 * Specific categories are shown, parent containers filtered out if child already matched.
 */
object ProficiencyTooltipProvider : BbfTooltipProvider {

    private val allTags = listOf(
        "Мечи" to BbfItemTags.PROFICIENCY_SWORDS,
        "Топоры (оружие)" to BbfItemTags.PROFICIENCY_AXES_WEAPON,
        "Воинское оружие" to BbfItemTags.PROFICIENCY_MARTIAL_WEAPONS,
        "Луки" to BbfItemTags.PROFICIENCY_BOWS,
        "Кузнечные инструменты" to BbfItemTags.PROFICIENCY_SMITHING_TOOLS,
        "Ремесленные инструменты" to BbfItemTags.PROFICIENCY_ARTISAN_TOOLS,
    )

    override fun getSections(stack: ItemStack, shiftHeld: Boolean): List<BbfTooltipSection> {
        val matched = allTags.filter { (_, tag) -> stack.isIn(tag) }
        if (matched.isEmpty()) return emptyList()

        return listOf(
            BbfTooltipSection(
                header = "Владение:",
                lines = matched.map { (name, _) -> name }
            )
        )
    }
}
