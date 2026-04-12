package omc.boundbyfate.registry

import net.minecraft.item.Item
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier
import omc.boundbyfate.api.combat.WeaponProperty

/**
 * Item tag keys for weapon properties.
 * Tags live in: data/boundbyfate-core/tags/items/weapon_property/<name>.json
 */
object BbfWeaponTags {
    val TWO_HANDED: TagKey<Item> = of("weapon_property/two_handed")
    val REACH: TagKey<Item>      = of("weapon_property/reach")
    val LIGHT: TagKey<Item>      = of("weapon_property/light")
    val HEAVY: TagKey<Item>      = of("weapon_property/heavy")
    val VERSATILE: TagKey<Item>  = of("weapon_property/versatile")
    val FINESSE: TagKey<Item>    = of("weapon_property/finesse")
    val THROWN: TagKey<Item>     = of("weapon_property/thrown")
    val LOADING: TagKey<Item>    = of("weapon_property/loading")

    /** All property tags mapped to their enum value. */
    val ALL: List<Pair<WeaponProperty, TagKey<Item>>> = listOf(
        WeaponProperty.TWO_HANDED to TWO_HANDED,
        WeaponProperty.REACH      to REACH,
        WeaponProperty.LIGHT      to LIGHT,
        WeaponProperty.HEAVY      to HEAVY,
        WeaponProperty.VERSATILE  to VERSATILE,
        WeaponProperty.FINESSE    to FINESSE,
        WeaponProperty.THROWN     to THROWN,
        WeaponProperty.LOADING    to LOADING,
    )

    private fun of(path: String): TagKey<Item> =
        TagKey.of(RegistryKeys.ITEM, Identifier("boundbyfate-core", path))
}
