package omc.boundbyfate.api.proficiency

import net.minecraft.util.Identifier

/**
 * A single entry within a proficiency definition.
 * Defines which items/blocks are covered and what penalty applies without proficiency.
 *
 * @property displayName Human-readable name (e.g. "Мечи", "Кузнечные инструменты")
 * @property items Specific item IDs covered by this entry
 * @property itemTags Item tag IDs covered by this entry (e.g. "minecraft:swords")
 * @property blocks Specific block IDs covered by this entry
 * @property blockTags Block tag IDs covered by this entry
 * @property penalty The penalty applied when proficiency is missing
 */
data class ProficiencyEntry(
    val displayName: String,
    val items: List<Identifier> = emptyList(),
    val itemTags: List<Identifier> = emptyList(),
    val blocks: List<Identifier> = emptyList(),
    val blockTags: List<Identifier> = emptyList(),
    val penalty: PenaltyConfig
)
