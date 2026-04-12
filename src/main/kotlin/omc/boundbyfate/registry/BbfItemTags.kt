package omc.boundbyfate.registry

import net.minecraft.item.Item
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier

/**
 * Item tag keys for proficiency categories.
 * These tags are used both for ProficiencySystem checks and client-side tooltips.
 *
 * Tag files live in: data/boundbyfate-core/tags/items/proficiency/<name>.json
 */
object BbfItemTags {

    val PROFICIENCY_SWORDS: TagKey<Item> = of("proficiency/swords")
    val PROFICIENCY_AXES_WEAPON: TagKey<Item> = of("proficiency/axes_weapon")
    val PROFICIENCY_MARTIAL_WEAPONS: TagKey<Item> = of("proficiency/martial_weapons")
    val PROFICIENCY_SMITHING_TOOLS: TagKey<Item> = of("proficiency/smithing_tools")
    val PROFICIENCY_ARTISAN_TOOLS: TagKey<Item> = of("proficiency/artisan_tools")

    /**
     * All proficiency tags with their display names, ordered from most specific to most general.
     * Used by tooltip mixin to show relevant categories.
     */
    val ALL: List<Pair<String, TagKey<Item>>> = listOf(
        "Мечи" to PROFICIENCY_SWORDS,
        "Топоры (оружие)" to PROFICIENCY_AXES_WEAPON,
        "Воинское оружие" to PROFICIENCY_MARTIAL_WEAPONS,
        "Кузнечные инструменты" to PROFICIENCY_SMITHING_TOOLS,
        "Ремесленные инструменты" to PROFICIENCY_ARTISAN_TOOLS,
    )

    private fun of(path: String): TagKey<Item> =
        TagKey.of(RegistryKeys.ITEM, Identifier("boundbyfate-core", path))
}
