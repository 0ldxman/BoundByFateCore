package omc.boundbyfate.api.combat

import net.minecraft.util.Identifier

/**
 * Defines combat properties for a weapon type.
 *
 * One definition can cover multiple items (e.g. all iron/diamond swords → "longsword").
 * Loaded from data/<namespace>/bbf_weapon/<name>.json
 *
 * @property id Unique identifier, e.g. "boundbyfate-core:longsword"
 * @property displayName Human-readable name shown in tooltips
 * @property items Item IDs covered by this definition
 * @property damage Damage dice expression, e.g. "1d8"
 * @property versatileDamage Damage dice when wielded two-handed (VERSATILE property only)
 * @property damageType BbF damage type identifier
 * @property properties Set of weapon properties
 */
data class WeaponDefinition(
    val id: Identifier,
    val displayName: String,
    val items: List<Identifier>,
    val damage: String,
    val versatileDamage: String? = null,
    val damageType: Identifier = Identifier("boundbyfate-core", "bludgeoning"),
    val properties: Set<WeaponProperty> = emptySet()
) {
    fun has(property: WeaponProperty): Boolean = property in properties
}
