package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.api.skill.ProficiencyLevel
import omc.boundbyfate.registry.SkillRegistry

/**
 * Immutable data class storing skill proficiency levels for an entity.
 *
 * Attached to entities via Fabric Data Attachment API.
 * All modifications return new instances (immutable pattern).
 *
 * @property proficiencies Map of skill/save ID to proficiency level (0, 1, 2)
 */
data class EntitySkillData(
    val proficiencies: Map<Identifier, Int> = emptyMap()
) {
    /**
     * Gets the proficiency level for a skill.
     *
     * @param skillId The skill identifier
     * @return ProficiencyLevel (NONE if not set)
     */
    fun getProficiency(skillId: Identifier): ProficiencyLevel {
        return ProficiencyLevel.fromInt(proficiencies[skillId] ?: 0)
    }

    /**
     * Returns a new EntitySkillData with updated proficiency for a skill.
     *
     * @param skillId The skill identifier
     * @param level The new proficiency level (0, 1, or 2)
     * @return New EntitySkillData instance
     */
    fun withProficiency(skillId: Identifier, level: ProficiencyLevel): EntitySkillData {
        SkillRegistry.getOrThrow(skillId) // Validate skill exists
        return copy(proficiencies = proficiencies + (skillId to level.multiplier))
    }

    /**
     * Returns a new EntitySkillData with a skill proficiency removed.
     *
     * @param skillId The skill identifier
     * @return New EntitySkillData instance
     */
    fun withoutProficiency(skillId: Identifier): EntitySkillData {
        return copy(proficiencies = proficiencies - skillId)
    }

    companion object {
        val CODEC: Codec<EntitySkillData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.unboundedMap(Identifier.CODEC, Codec.INT)
                    .optionalFieldOf("proficiencies", emptyMap())
                    .forGetter { it.proficiencies }
            ).apply(instance, ::EntitySkillData)
        }

        /**
         * Creates EntitySkillData from a map of skill ID strings to proficiency levels.
         * Invalid skill IDs are ignored with a warning.
         */
        fun fromMap(raw: Map<Identifier, Int>): EntitySkillData {
            val validated = raw.filter { (id, value) ->
                SkillRegistry.contains(id) && value in 0..2
            }
            return EntitySkillData(proficiencies = validated)
        }
    }
}
