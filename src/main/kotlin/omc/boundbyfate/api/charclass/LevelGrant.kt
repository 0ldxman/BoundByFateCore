package omc.boundbyfate.api.charclass

import net.minecraft.util.Identifier

/**
 * Defines what a character receives at a specific class level.
 *
 * @property resources Map of resource ID to maximum value granted at this level.
 * @property proficiencies List of skill/save IDs granted at this level.
 * @property itemProficiencies List of proficiency IDs (weapons/armor/tools) granted at this level.
 * @property features List of Feature IDs granted at this level (passive properties, triggered reactions).
 * @property abilities List of Ability IDs granted at this level (active actions added to hotbar).
 * @property upgrade True if this level grants an ASI/Feat choice slot.
 */
data class LevelGrant(
    val resources: Map<Identifier, Int> = emptyMap(),
    val proficiencies: List<Identifier> = emptyList(),
    val itemProficiencies: List<Identifier> = emptyList(),
    val features: List<Identifier> = emptyList(),
    val abilities: List<Identifier> = emptyList(),
    val upgrade: Boolean = false
)
