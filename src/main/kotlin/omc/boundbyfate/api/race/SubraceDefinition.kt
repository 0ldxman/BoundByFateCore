package omc.boundbyfate.api.race

import net.minecraft.util.Identifier

/**
 * Immutable definition of a subrace.
 *
 * Loaded from data/<namespace>/bbf_subrace/<name>.json
 *
 * A subrace OVERRIDES the parent race's fields — it does not add on top.
 * Any field set in the subrace replaces the corresponding field from the race.
 * Fields left null in the subrace fall back to the parent race's value.
 *
 * Example: Mountain Dwarf overrides statBonuses (+2 STR +2 CON instead of just +2 CON)
 * and features (different racial features than base Dwarf).
 *
 * @property id Unique identifier (e.g. "boundbyfate-core:hill_dwarf")
 * @property displayName Human-readable name shown instead of race name
 * @property parentRace The race this subrace belongs to
 * @property size Override size (null = use race's value)
 * @property scaleOverride Override scale (null = use race's value)
 * @property speedFt Override speed in ft (null = use race's value)
 * @property statBonuses Override stat bonuses (null = use race's value)
 * @property senses Override senses (null = use race's value)
 * @property resistances Override resistances (null = use race's value)
 * @property proficiencies Override skill/save proficiencies (null = use race's value)
 * @property itemProficiencies Override item proficiencies (null = use race's value)
 * @property features Override features (null = use race's value)
 */
data class SubraceDefinition(
    val id: Identifier,
    val displayName: String,
    val parentRace: Identifier,
    val size: RaceSize? = null,
    val scaleOverride: Float? = null,
    val speedFt: Int? = null,
    val statBonuses: Map<Identifier, Int>? = null,
    val senses: RaceSenses? = null,
    val resistances: Map<Identifier, Int>? = null,
    val proficiencies: List<Identifier>? = null,
    val itemProficiencies: List<Identifier>? = null,
    val features: List<Identifier>? = null
) {
    init {
        require(displayName.isNotBlank()) { "SubraceDefinition $id: displayName cannot be blank" }
    }

    /**
     * Resolves the effective race data by merging subrace overrides onto the parent race.
     * Subrace fields take priority; null fields fall back to the parent race.
     */
    fun resolve(race: RaceDefinition): ResolvedRaceData = ResolvedRaceData(
        displayName = displayName,
        size = size ?: race.size,
        scaleOverride = scaleOverride ?: race.scaleOverride,
        speedFt = speedFt ?: race.speedFt,
        statBonuses = statBonuses ?: race.statBonuses,
        senses = senses ?: race.senses,
        resistances = resistances ?: race.resistances,
        proficiencies = proficiencies ?: race.proficiencies,
        itemProficiencies = itemProficiencies ?: race.itemProficiencies,
        features = features ?: race.features
    )
}

/**
 * Resolved race data after merging subrace overrides onto the parent race.
 * This is what actually gets applied to the player.
 */
data class ResolvedRaceData(
    val displayName: String,
    val size: RaceSize,
    val scaleOverride: Float?,
    val speedFt: Int,
    val statBonuses: Map<Identifier, Int>,
    val senses: RaceSenses,
    val resistances: Map<Identifier, Int>,
    val proficiencies: List<Identifier>,
    val itemProficiencies: List<Identifier>,
    val features: List<Identifier>
)
