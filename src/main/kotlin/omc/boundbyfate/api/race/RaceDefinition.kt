package omc.boundbyfate.api.race

import net.minecraft.util.Identifier

/**
 * Immutable definition of a playable race.
 *
 * Loaded from data/<namespace>/bbf_race/<name>.json
 *
 * A race provides exactly:
 * - Stat bonuses (увеличение характеристик)
 * - Size category (размер)
 * - Scale override (размер модельки)
 * - Walking speed in ft (скорость)
 * - Features (особенности) — references to bbf_feature definitions
 *
 * Everything else (darkvision, resistances, proficiencies, etc.) is
 * implemented as Features and referenced via the features list.
 */
data class RaceDefinition(
    val id: Identifier,
    val displayName: String,
    val size: RaceSize = RaceSize.MEDIUM,
    val scaleOverride: Float? = null,
    val speedFt: Int = 30,
    val statBonuses: Map<Identifier, Int> = emptyMap(),
    val features: List<Identifier> = emptyList(),
    val subraces: List<Identifier> = emptyList()
) {
    init {
        require(displayName.isNotBlank()) { "RaceDefinition $id: displayName cannot be blank" }
    }
}
