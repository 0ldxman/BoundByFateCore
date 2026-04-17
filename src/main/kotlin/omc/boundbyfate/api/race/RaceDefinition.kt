package omc.boundbyfate.api.race

import net.minecraft.util.Identifier

/**
 * Immutable definition of a playable race.
 *
 * Loaded from data/<namespace>/bbf_race/<name>.json
 *
 * @property id Unique identifier (e.g. "boundbyfate-core:dwarf")
 * @property displayName Human-readable name
 * @property size Physical size category (affects scale and future mechanics)
 * @property speedFt Walking speed in D&D feet (30 = normal human speed)
 * @property statBonuses Flat bonuses added to base stats
 * @property senses Sensory capabilities
 * @property resistances Damage type resistance levels (sourceId = race id)
 * @property proficiencies Skill/save proficiency IDs granted
 * @property itemProficiencies Weapon/armor/tool proficiency IDs granted
 * @property abilities Ability IDs granted (stubs for future ability system)
 * @property subraces IDs of available subraces (empty = no subraces)
 */
data class RaceDefinition(
    val id: Identifier,
    val displayName: String,
    val size: RaceSize = RaceSize.MEDIUM,
    val scaleOverride: Float? = null,
    val speedFt: Int = 30,
    val statBonuses: Map<Identifier, Int> = emptyMap(),
    val senses: RaceSenses = RaceSenses(),
    val resistances: Map<Identifier, Int> = emptyMap(),
    val proficiencies: List<Identifier> = emptyList(),
    val itemProficiencies: List<Identifier> = emptyList(),
    val abilities: List<Identifier> = emptyList(),
    val subraces: List<Identifier> = emptyList()
) {
    init {
        require(displayName.isNotBlank()) { "RaceDefinition $id: displayName cannot be blank" }
    }
}
