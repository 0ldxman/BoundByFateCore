package omc.boundbyfate.api.proficiency

import net.minecraft.item.Item
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier

/**
 * Defines a proficiency - a skill that grants the ability to use certain items/blocks.
 *
 * Proficiencies can be hierarchical: a parent proficiency (e.g. "Воинское оружие")
 * can include child proficiencies (e.g. "Мечи", "Топоры").
 * Having the parent automatically grants all children.
 *
 * Two types:
 * 1. **Container** - has [includes], no [itemTag]/[blockTags]/[penalty].
 *    Example: "Воинское оружие" includes "Мечи", "Топоры"
 *
 * 2. **Leaf** - has [itemTag] and/or [blockTags] + [penalty].
 *    The item tag is the single source of truth for which items require this proficiency.
 *    Example: "Мечи" → itemTag = "boundbyfate-core:proficiency/swords"
 *
 * @property id Unique identifier, e.g. "boundbyfate-core:swords"
 * @property displayName Human-readable name shown in tooltips and messages
 * @property includes IDs of child proficiencies (container only)
 * @property itemTag Item tag that covers this proficiency (leaf only). Null for containers.
 * @property blockTags Block tag IDs that require this proficiency (leaf only)
 * @property blocks Explicit block IDs that require this proficiency (leaf only)
 * @property penalty Penalty applied when proficiency is missing (leaf only)
 */
data class ProficiencyDefinition(
    val id: Identifier,
    val displayName: String,
    val includes: List<Identifier> = emptyList(),
    val itemTag: TagKey<Item>? = null,
    val blockTags: List<Identifier> = emptyList(),
    val blocks: List<Identifier> = emptyList(),
    val penalty: PenaltyConfig? = null
) {
    /** True if this is a container proficiency (groups others) */
    val isContainer: Boolean get() = includes.isNotEmpty()

    /** True if this is a leaf proficiency (has direct item/block coverage) */
    val isLeaf: Boolean get() = itemTag != null || blockTags.isNotEmpty() || blocks.isNotEmpty()

    companion object {
        fun itemTagKey(id: Identifier): TagKey<Item> =
            TagKey.of(RegistryKeys.ITEM, id)
    }
}
