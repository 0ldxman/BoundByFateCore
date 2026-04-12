package omc.boundbyfate.api.feat

import net.minecraft.util.Identifier

/**
 * What a feat grants when taken.
 *
 * @property statBonuses Flat bonuses added to base stats (e.g. +1 CON)
 * @property proficiencies Skill/save proficiency IDs granted
 * @property itemProficiencies Weapon/armor/tool proficiency IDs granted
 * @property abilities Ability IDs granted (stubs for future ability system)
 */
data class FeatGrant(
    val statBonuses: Map<Identifier, Int> = emptyMap(),
    val proficiencies: List<Identifier> = emptyList(),
    val itemProficiencies: List<Identifier> = emptyList(),
    val abilities: List<Identifier> = emptyList()
)
