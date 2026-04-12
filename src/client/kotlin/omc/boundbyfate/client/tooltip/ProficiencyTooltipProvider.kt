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
        "Кузнечные инструменты" to BbfItemTags.PROFICIENCY_SMITHING_TOOLS,
        "Ремесленные инструменты" to BbfItemTags.PROFICIENCY_ARTISAN_TOOLS,
    )

    override fun getSections(stack: ItemStack, shiftHeld: Boolean): List<BbfTooltipSection> {
        val matched = allTags.filter { (_, tag) -> stack.isIn(tag) }
        if (matched.isEmpty()) return emptyList()

        // Filter out parent containers when a more specific child already matched
        val specific = matched.filter { (_, tag) ->
            // Keep this tag if no OTHER matched tag is a child of it
            // (i.e. remove parents when their children are also matched)
            matched.none { (_, other) -> other != tag && isChildOf(other, tag) }
        }

        val names = specific.map { (name, _) -> name }

        return listOf(
            BbfTooltipSection(
                header = "Владение:",
                lines = names
            )
        )
    }

    private fun isChildOf(
        child: net.minecraft.registry.tag.TagKey<*>,
        parent: net.minecraft.registry.tag.TagKey<*>
    ): Boolean {
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
