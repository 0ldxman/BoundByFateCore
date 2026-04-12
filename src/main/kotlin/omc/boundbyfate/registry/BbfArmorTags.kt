package omc.boundbyfate.registry

import net.minecraft.item.Item
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier

/**
 * Item tag keys for armor type classification.
 * Tags live in: data/boundbyfate-core/tags/items/armor_type/<name>.json
 *
 * Armor type determines:
 * - DEX bonus cap applied to AC
 * - Whether STR requirement is checked (heavy only)
 */
object BbfArmorTags {
    /** Light armor: full DEX bonus to AC, no STR requirement */
    val LIGHT: TagKey<Item> = of("armor_type/light")

    /** Medium armor: DEX bonus capped at +2 */
    val MEDIUM: TagKey<Item> = of("armor_type/medium")

    /** Heavy armor: no DEX bonus, may require minimum STR */
    val HEAVY: TagKey<Item> = of("armor_type/heavy")

    /** Shield: grants flat +2 AC when held in off-hand */
    val SHIELD: TagKey<Item> = of("armor_type/shield")

    private fun of(path: String): TagKey<Item> =
        TagKey.of(RegistryKeys.ITEM, Identifier("boundbyfate-core", path))
}
