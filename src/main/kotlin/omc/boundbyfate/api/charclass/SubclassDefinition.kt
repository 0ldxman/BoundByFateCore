package omc.boundbyfate.api.charclass

import net.minecraft.util.Identifier

/**
 * Immutable definition of a class subclass (archetype).
 *
 * Loaded from JSON files in data/<namespace>/bbf_subclass/<id>.json
 *
 * @property id Unique identifier (e.g. "boundbyfate-core:battle_master")
 * @property displayName Display name (e.g. "Мастер боя")
 * @property parentClass The class this subclass belongs to
 * @property progression Additional grants per level (stacks with class progression)
 */
data class SubclassDefinition(
    val id: Identifier,
    val displayName: String,
    val parentClass: Identifier,
    val progression: Map<Int, LevelGrant> = emptyMap()
) {
    init {
        require(displayName.isNotBlank()) { "SubclassDefinition $id: displayName cannot be blank" }
    }

    fun getGrantsUpTo(level: Int): List<LevelGrant> {
        return (1..level.coerceIn(1, 20)).mapNotNull { progression[it] }
    }

    fun getGrantAt(level: Int): LevelGrant? = progression[level]
}
