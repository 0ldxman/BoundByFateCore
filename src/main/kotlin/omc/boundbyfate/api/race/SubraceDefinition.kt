package omc.boundbyfate.api.race

import net.minecraft.util.Identifier

/**
 * Immutable definition of a subrace.
 *
 * Loaded from data/<namespace>/bbf_subrace/<name>.json
 *
 * A subrace OVERRIDES the parent race's fields.
 * Any field set in the subrace replaces the corresponding field from the race.
 * Fields left null fall back to the parent race's value.
 *
 * Example: Mountain Dwarf overrides statBonuses (+2 STR +2 CON instead of +2 CON)
 * and features (different racial features than base Dwarf).
 */
data class SubraceDefinition(
    val id: Identifier,
    val displayName: String,
    val parentRace: Identifier,
    val size: RaceSize? = null,
    val scaleOverride: Float? = null,
    val speedFt: Int? = null,
    val statBonuses: Map<Identifier, Int>? = null,
    val features: List<Identifier>? = null
) {
    init {
        require(displayName.isNotBlank()) { "SubraceDefinition $id: displayName cannot be blank" }
    }

    /**
     * Resolves effective race data by merging subrace overrides onto the parent race.
     */
    fun resolve(race: RaceDefinition): ResolvedRaceData = ResolvedRaceData(
        displayName = displayName,
        size = size ?: race.size,
        scaleOverride = scaleOverride ?: race.scaleOverride,
        speedFt = speedFt ?: race.speedFt,
        statBonuses = statBonuses ?: race.statBonuses,
        features = features ?: race.features
    )
}

/**
 * Resolved race data after merging subrace overrides onto the parent race.
 */
data class ResolvedRaceData(
    val displayName: String,
    val size: RaceSize,
    val scaleOverride: Float?,
    val speedFt: Int,
    val statBonuses: Map<Identifier, Int>,
    val features: List<Identifier>
)
