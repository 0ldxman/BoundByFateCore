package omc.boundbyfate.api.proficiency

import net.minecraft.util.Identifier

/**
 * Defines a proficiency - a skill that grants the ability to use certain items/blocks.
 *
 * Proficiencies can be hierarchical: a parent proficiency (e.g. "Воинское оружие")
 * can include child proficiencies (e.g. "Мечи", "Топоры").
 * Having the parent automatically grants all children.
 *
 * Two types of proficiency:
 * 1. **Container** - has [includes] but no [entries]. Groups other proficiencies.
 *    Example: "Воинское оружие" includes "Мечи", "Топоры", "Луки"
 *
 * 2. **Leaf** - has [entries] with items/blocks and penalties. No [includes].
 *    Example: "Мечи" covers minecraft:swords tag with attack_chance penalty
 *
 * @property id Unique identifier
 * @property displayName Human-readable name
 * @property includes IDs of child proficiencies (for container type)
 * @property entries Item/block entries with penalties (for leaf type)
 */
data class ProficiencyDefinition(
    val id: Identifier,
    val displayName: String,
    val includes: List<Identifier> = emptyList(),
    val entries: List<ProficiencyEntry> = emptyList()
) {
    /** True if this is a container proficiency (groups others) */
    val isContainer: Boolean get() = includes.isNotEmpty()

    /** True if this is a leaf proficiency (has direct item/block entries) */
    val isLeaf: Boolean get() = entries.isNotEmpty()
}
