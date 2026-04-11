package omc.boundbyfate.api.skill

import net.minecraft.util.Identifier

/**
 * Immutable definition of a skill or saving throw.
 *
 * Skills and saving throws share the same structure - both are linked
 * to a stat and use proficiency bonuses. Use [isSavingThrow] to distinguish.
 *
 * @property id Unique identifier (e.g. "boundbyfate-core:athletics")
 * @property displayName Full display name (e.g. "Атлетика")
 * @property linkedStat The stat this skill is based on
 * @property isSavingThrow True if this is a saving throw, false if a skill
 */
data class SkillDefinition(
    val id: Identifier,
    val displayName: String,
    val linkedStat: Identifier,
    val isSavingThrow: Boolean = false
) {
    init {
        require(displayName.isNotBlank()) { "SkillDefinition $id: displayName cannot be blank" }
    }
}
