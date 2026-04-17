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
 * @property hpPerLevel HP gained per level after 1st
 * @property subclassLevel Level at which subclass is chosen (usually 3)
 * @property mechanics IDs of class-specific game mechanics (e.g. spellcasting style, metamagic).
 *                     These are identifiers for future subsystems — registered separately.
 * @property progression Map of class level (1-20) to what is granted at that level
 */
data class ClassDefinition(
    val id: Identifier,
    val displayName: String,
    val hitDie: Int,
    val hpPerLevel: Int = hitDie / 2 + 1,
    val subclassLevel: Int = 3,
    val mechanics: List<Identifier> = emptyList(),
    val progression: Map<Int, LevelGrant> = emptyMap()
) {
    init {
        require(displayName.isNotBlank()) { "ClassDefinition $id: displayName cannot be blank" }
        require(hitDie in setOf(4, 6, 8, 10, 12)) { "ClassDefinition $id: hitDie must be 4, 6, 8, 10, or 12" }
        require(hpPerLevel >= 1) { "ClassDefinition $id: hpPerLevel must be >= 1" }
        require(subclassLevel in 1..20) { "ClassDefinition $id: subclassLevel must be 1-20" }
    }

    fun getGrantsUpTo(level: Int): List<LevelGrant> =
        (1..level.coerceIn(1, 20)).mapNotNull { progression[it] }

    fun getGrantAt(level: Int): LevelGrant? = progression[level]
}
