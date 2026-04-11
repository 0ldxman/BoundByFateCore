package omc.boundbyfate.api.charclass

import net.minecraft.util.Identifier

/**
 * Immutable definition of a character class.
 *
 * Loaded from JSON files in data/<namespace>/bbf_class/<id>.json
 *
 * @property id Unique identifier (e.g. "boundbyfate-core:fighter")
 * @property displayName Display name (e.g. "Воин")
 * @property hitDie Hit die sides (6, 8, 10, or 12)
 * @property subclassLevel Level at which subclass is chosen (usually 3)
 * @property progression Map of class level (1-20) to what is granted at that level
 */
data class ClassDefinition(
    val id: Identifier,
    val displayName: String,
    val hitDie: Int,
    val subclassLevel: Int = 3,
    val progression: Map<Int, LevelGrant> = emptyMap()
) {
    init {
        require(displayName.isNotBlank()) { "ClassDefinition $id: displayName cannot be blank" }
        require(hitDie in setOf(6, 8, 10, 12)) { "ClassDefinition $id: hitDie must be 6, 8, 10, or 12" }
        require(subclassLevel in 1..20) { "ClassDefinition $id: subclassLevel must be 1-20" }
    }

    /**
     * Returns all grants accumulated from level 1 up to [level].
     * Used when initializing a character at a given level.
     */
    fun getGrantsUpTo(level: Int): List<LevelGrant> {
        return (1..level.coerceIn(1, 20)).mapNotNull { progression[it] }
    }

    /**
     * Returns the grant for a specific level, or null if nothing is granted.
     */
    fun getGrantAt(level: Int): LevelGrant? = progression[level]
}
