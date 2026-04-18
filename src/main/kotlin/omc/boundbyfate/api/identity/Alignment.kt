package omc.boundbyfate.api.identity

/**
 * Represents the 9 alignment types in D&D.
 */
enum class Alignment(val translationKey: String) {
    LAWFUL_GOOD("bbf.alignment.lawful_good"),
    NEUTRAL_GOOD("bbf.alignment.neutral_good"),
    CHAOTIC_GOOD("bbf.alignment.chaotic_good"),
    LAWFUL_NEUTRAL("bbf.alignment.lawful_neutral"),
    TRUE_NEUTRAL("bbf.alignment.true_neutral"),
    CHAOTIC_NEUTRAL("bbf.alignment.chaotic_neutral"),
    LAWFUL_EVIL("bbf.alignment.lawful_evil"),
    NEUTRAL_EVIL("bbf.alignment.neutral_evil"),
    CHAOTIC_EVIL("bbf.alignment.chaotic_evil");

    companion object {
        fun from(lawChaos: AlignmentAxis, goodEvil: AlignmentAxis): Alignment {
            return when {
                lawChaos == AlignmentAxis.LAWFUL && goodEvil == AlignmentAxis.GOOD -> LAWFUL_GOOD
                lawChaos == AlignmentAxis.NEUTRAL_LC && goodEvil == AlignmentAxis.GOOD -> NEUTRAL_GOOD
                lawChaos == AlignmentAxis.CHAOTIC && goodEvil == AlignmentAxis.GOOD -> CHAOTIC_GOOD
                lawChaos == AlignmentAxis.LAWFUL && goodEvil == AlignmentAxis.NEUTRAL_GE -> LAWFUL_NEUTRAL
                lawChaos == AlignmentAxis.NEUTRAL_LC && goodEvil == AlignmentAxis.NEUTRAL_GE -> TRUE_NEUTRAL
                lawChaos == AlignmentAxis.CHAOTIC && goodEvil == AlignmentAxis.NEUTRAL_GE -> CHAOTIC_NEUTRAL
                lawChaos == AlignmentAxis.LAWFUL && goodEvil == AlignmentAxis.EVIL -> LAWFUL_EVIL
                lawChaos == AlignmentAxis.NEUTRAL_LC && goodEvil == AlignmentAxis.EVIL -> NEUTRAL_EVIL
                else -> CHAOTIC_EVIL
            }
        }
    }

    fun getShortKey(): String = "${translationKey}.short"
}

/**
 * Represents the axes of alignment.
 */
enum class AlignmentAxis {
    // Law-Chaos axis
    LAWFUL,
    NEUTRAL_LC,
    CHAOTIC,
    
    // Good-Evil axis
    GOOD,
    NEUTRAL_GE,
    EVIL
}
