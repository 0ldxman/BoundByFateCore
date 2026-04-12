package omc.boundbyfate.api.feat

import net.minecraft.util.Identifier

/**
 * Prerequisites that must be met to take a feat.
 *
 * @property minStats Minimum stat values required (e.g. STR >= 13)
 * @property requiredProficiencies Required skill/save proficiency IDs
 * @property requiredItemProficiencies Required weapon/armor/tool proficiency IDs
 * @property requiredFeats Other feats that must be taken first
 */
data class FeatPrerequisites(
    val minStats: Map<Identifier, Int> = emptyMap(),
    val requiredProficiencies: List<Identifier> = emptyList(),
    val requiredItemProficiencies: List<Identifier> = emptyList(),
    val requiredFeats: List<Identifier> = emptyList()
)
