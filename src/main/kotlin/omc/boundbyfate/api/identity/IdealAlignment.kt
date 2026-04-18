package omc.boundbyfate.api.identity

/**
 * Represents which alignment axis an ideal is tied to.
 * An ideal can be tied to multiple axes.
 */
enum class IdealAlignment(val translationKey: String) {
    GOOD("bbf.alignment.axis.good"),
    EVIL("bbf.alignment.axis.evil"),
    LAWFUL("bbf.alignment.axis.lawful"),
    CHAOTIC("bbf.alignment.axis.chaotic"),
    NEUTRAL("bbf.alignment.axis.neutral"),
    ANY("bbf.alignment.axis.any");  // always compatible

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
        NEUTRAL -> alignment in listOf(
            Alignment.TRUE_NEUTRAL, Alignment.LAWFUL_NEUTRAL, Alignment.CHAOTIC_NEUTRAL,
            Alignment.NEUTRAL_GOOD, Alignment.NEUTRAL_EVIL
        )
        ANY -> true
    }
}
