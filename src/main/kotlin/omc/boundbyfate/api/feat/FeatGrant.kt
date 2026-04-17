package omc.boundbyfate.api.feat

import net.minecraft.util.Identifier

/**
 * What a feat grants when taken.
 *
 * A feat is a container — it grants Features (passive properties) and/or
 * Abilities (active actions), plus optional stat bonuses and proficiencies.
 *
 * @property statBonuses Flat bonuses added to base stats (e.g. +1 CON)
 * @property proficiencies Skill/save proficiency IDs granted
 * @property itemProficiencies Weapon/armor/tool proficiency IDs granted
 * @property features Feature IDs granted (passive properties, triggered reactions)
 * @property abilities Ability IDs granted directly (active actions added to hotbar)
 */
data class FeatGrant(
    val statBonuses: Map<Identifier, Int> = emptyMap(),
    val proficiencies: List<Identifier> = emptyList(),
    val itemProficiencies: List<Identifier> = emptyList(),
    val features: List<Identifier> = emptyList(),
    val abilities: List<Identifier> = emptyList()
)
