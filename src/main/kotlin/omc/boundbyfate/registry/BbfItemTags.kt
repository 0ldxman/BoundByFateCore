package omc.boundbyfate.registry

import net.minecraft.item.Item
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier

/**
 * Convenience TagKey constants for built-in proficiency item tags.
 * Tag files: data/boundbyfate-core/tags/items/proficiency/<name>.json
 *
 * These are the single source of truth for which items belong to each proficiency.
 * ProficiencySystem uses these tags for penalty checks; tooltips derive from ProficiencyRegistry.
 */
object BbfItemTags {
    val PROFICIENCY_SWORDS: TagKey<Item> = of("proficiency/swords")
    val PROFICIENCY_AXES_WEAPON: TagKey<Item> = of("proficiency/axes_weapon")
    val PROFICIENCY_MARTIAL_WEAPONS: TagKey<Item> = of("proficiency/martial_weapons")
    val PROFICIENCY_SMITHING_TOOLS: TagKey<Item> = of("proficiency/smithing_tools")
    val PROFICIENCY_ARTISAN_TOOLS: TagKey<Item> = of("proficiency/artisan_tools")
    val PROFICIENCY_BOWS: TagKey<Item> = of("proficiency/bows")

    private fun of(path: String): TagKey<Item> =
        TagKey.of(RegistryKeys.ITEM, Identifier("boundbyfate-core", path))
}
