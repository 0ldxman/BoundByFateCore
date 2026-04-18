package omc.boundbyfate.api.identity

/**
 * Represents which alignment axis an ideal is tied to.
 *
 * Two separate neutral axes:
 * - NEUTRAL_GE: neutral on the Good-Evil axis (LN, TN, CN)
 * - NEUTRAL_LC: neutral on the Law-Chaos axis (NG, TN, NE)
 * - TRUE_NEUTRAL: only True Neutral (both axes neutral)
 */
enum class IdealAlignment(val translationKey: String) {
    GOOD("bbf.alignment.axis.good"),
    EVIL("bbf.alignment.axis.evil"),
    LAWFUL("bbf.alignment.axis.lawful"),
    CHAOTIC("bbf.alignment.axis.chaotic"),
    NEUTRAL_GE("bbf.alignment.axis.neutral_ge"),   // neutral on Good-Evil axis
    NEUTRAL_LC("bbf.alignment.axis.neutral_lc"),   // neutral on Law-Chaos axis
    TRUE_NEUTRAL("bbf.alignment.axis.true_neutral"), // only True Neutral
    ANY("bbf.alignment.axis.any");                  // always compatible

    /**
     * Checks if this axis is compatible with the given alignment.
     */
    fun isCompatibleWith(alignment: Alignment): Boolean = when (this) {
        GOOD -> alignment in listOf(
            Alignment.LAWFUL_GOOD, Alignment.NEUTRAL_GOOD, Alignment.CHAOTIC_GOOD
        )
        EVIL -> alignment in listOf(
            Alignment.LAWFUL_EVIL, Alignment.NEUTRAL_EVIL, Alignment.CHAOTIC_EVIL
        )
        LAWFUL -> alignment in listOf(
            Alignment.LAWFUL_GOOD, Alignment.LAWFUL_NEUTRAL, Alignment.LAWFUL_EVIL
        )
        CHAOTIC -> alignment in listOf(
            Alignment.CHAOTIC_GOOD, Alignment.CHAOTIC_NEUTRAL, Alignment.CHAOTIC_EVIL
        )
        NEUTRAL_GE -> alignment in listOf(
            // Neutral on Good-Evil axis: Lawful Neutral, True Neutral, Chaotic Neutral
            Alignment.LAWFUL_NEUTRAL, Alignment.TRUE_NEUTRAL, Alignment.CHAOTIC_NEUTRAL
        )
        NEUTRAL_LC -> alignment in listOf(
            // Neutral on Law-Chaos axis: Neutral Good, True Neutral, Neutral Evil
            Alignment.NEUTRAL_GOOD, Alignment.TRUE_NEUTRAL, Alignment.NEUTRAL_EVIL
        )
        TRUE_NEUTRAL -> alignment == Alignment.TRUE_NEUTRAL
        ANY -> true
    }
}
