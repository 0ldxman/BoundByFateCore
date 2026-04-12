package omc.boundbyfate.api.race

import net.minecraft.util.Identifier

/**
 * Immutable definition of a subrace.
 *
 * Loaded from data/<namespace>/bbf_subrace/<name>.json
 * Grants additional bonuses on top of the parent race.
 *
 * @property id Unique identifier (e.g. "boundbyfate-core:hill_dwarf")
 * @property displayName Human-readable name
 * @property parentRace The race this subrace belongs to
 * @property statBonuses Additional stat bonuses
 * @property resistances Additional resistances
 * @property proficiencies Additional skill/save proficiencies
 * @property itemProficiencies Additional weapon/armor/tool proficiencies
 * @property abilities Additional ability IDs
 */
data class SubraceDefinition(
    val id: Identifier,
    val displayName: String,
    val parentRace: Identifier,
    val statBonuses: Map<Identifier, Int> = emptyMap(),
    val resistances: Map<Identifier, Int> = emptyMap(),
    val proficiencies: List<Identifier> = emptyList(),
    val itemProficiencies: List<Identifier> = emptyList(),
    val abilities: List<Identifier> = emptyList()
) {
    init {
        require(displayName.isNotBlank()) { "SubraceDefinition $id: displayName cannot be blank" }
    }
}
