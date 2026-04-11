package omc.boundbyfate.api.damage

/**
 * Resistance level for a damage type.
 *
 * Levels stack additively from all sources.
 * Final level is clamped to [-3, +2].
 *
 * Level → Damage multiplier:
 * - IMMUNITY       (-3) → 0.0x
 * - STRONG_RESIST  (-2) → 0.25x
 * - RESIST         (-1) → 0.5x
 * - NORMAL         ( 0) → 1.0x
 * - VULNERABLE     (+1) → 2.0x
 * - VERY_VULNERABLE(+2) → 4.0x
 */
enum class ResistanceLevel(val value: Int, val multiplier: Float, val displayName: String) {
    IMMUNITY        (-3, 0.00f, "Иммунитет"),
    STRONG_RESIST   (-2, 0.25f, "Сильное сопротивление"),
    RESIST          (-1, 0.50f, "Сопротивление"),
    NORMAL          ( 0, 1.00f, "Норма"),
    VULNERABLE      (+1, 2.00f, "Уязвимость"),
    VERY_VULNERABLE (+2, 4.00f, "Сильная уязвимость");

    companion object {
        val MIN = -3
        val MAX = +2

        /** Converts a summed integer level to the corresponding enum entry. */
        fun fromLevel(level: Int): ResistanceLevel {
            val clamped = level.coerceIn(MIN, MAX)
            return entries.first { it.value == clamped }
        }
    }
}
