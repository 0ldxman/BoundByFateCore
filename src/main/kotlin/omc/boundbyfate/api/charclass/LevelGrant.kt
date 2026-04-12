package omc.boundbyfate.api.charclass

import net.minecraft.util.Identifier

/**
 * Defines what a character receives at a specific class level.
 *
 * @property resources Map of resource ID to maximum value granted at this level.
 * @property proficiencies List of skill/save IDs granted at this level.
 * @property itemProficiencies List of proficiency IDs (weapons/armor/tools) granted at this level.
 * @property abilities List of ability IDs granted at this level (stubs for now).
 */
data class LevelGrant(
    val resources: Map<Identifier, Int> = emptyMap(),
    val proficiencies: List<Identifier> = emptyList(),
    val itemProficiencies: List<Identifier> = emptyList(),
    val abilities: List<Identifier> = emptyList()
)
