package omc.boundbyfate.api.skill

/**
 * Proficiency level for a skill or saving throw.
 *
 * Determines the proficiency bonus multiplier:
 * - NONE       → multiplier 0 (no bonus)
 * - PROFICIENT → multiplier 1 (full proficiency bonus)
 * - EXPERTISE  → multiplier 2 (double proficiency bonus)
 *
 * Formula: skillBonus = stat.modifier + (proficiencyBonus * level.multiplier)
 */
enum class ProficiencyLevel(val multiplier: Int) {
    NONE(0),
    PROFICIENT(1),
    EXPERTISE(2);

    companion object {
        /**
         * Creates a ProficiencyLevel from an integer (0, 1, or 2).
         * Invalid values default to NONE.
         */
        fun fromInt(value: Int): ProficiencyLevel = when (value) {
            1 -> PROFICIENT
            2 -> EXPERTISE
            else -> NONE
        }
    }
}
