package omc.boundbyfate.api.identity

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Represents alignment as coordinates on a 2D grid.
 * 
 * The grid is 15x15 (from -6 to +6 on each axis), divided into 9 zones of 5x5 each.
 * - lawChaos: -6 (Lawful) to +6 (Chaotic)
 * - goodEvil: -6 (Evil) to +6 (Good)
 * 
 * Center (0, 0) is True Neutral.
 * Borders at ±2 and ±3 indicate wavering conviction.
 */
data class AlignmentCoordinates(
    val lawChaos: Int,
    val goodEvil: Int
) {
    init {
        require(lawChaos in -6..6) { "lawChaos must be in range -6..6, got $lawChaos" }
        require(goodEvil in -6..6) { "goodEvil must be in range -6..6, got $goodEvil" }
    }

    /**
     * Returns the alignment zone based on current coordinates.
     */
    fun getAlignment(): Alignment {
        val lcZone = when {
            lawChaos <= -3 -> AlignmentAxis.LAWFUL
            lawChaos >= 3 -> AlignmentAxis.CHAOTIC
            else -> AlignmentAxis.NEUTRAL_LC
        }
        val geZone = when {
            goodEvil >= 3 -> AlignmentAxis.GOOD
            goodEvil <= -3 -> AlignmentAxis.EVIL
            else -> AlignmentAxis.NEUTRAL_GE
        }
        return Alignment.from(lcZone, geZone)
    }

    /**
     * Checks if coordinates are on the border between zones.
     * Border coordinates are ±2 and ±3 (wavering conviction).
     * 
     * @return Pair of (lawChaos is on border, goodEvil is on border)
     */
    fun isOnBorder(): Pair<Boolean, Boolean> {
        val lcBorder = lawChaos in listOf(-3, -2, 2, 3)
        val geBorder = goodEvil in listOf(-3, -2, 2, 3)
        return lcBorder to geBorder
    }

    /**
     * Adds values to coordinates, clamping to valid range.
     */
    fun add(lawChaosChange: Int, goodEvilChange: Int): AlignmentCoordinates {
        return AlignmentCoordinates(
            (lawChaos + lawChaosChange).coerceIn(-6, 6),
            (goodEvil + goodEvilChange).coerceIn(-6, 6)
        )
    }

    companion object {
        val CODEC: Codec<AlignmentCoordinates> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("lawChaos").forGetter(AlignmentCoordinates::lawChaos),
                Codec.INT.fieldOf("goodEvil").forGetter(AlignmentCoordinates::goodEvil)
            ).apply(instance, ::AlignmentCoordinates)
        }

        /**
         * Creates coordinates from alignment zone (center of zone).
         */
        fun fromAlignment(alignment: Alignment): AlignmentCoordinates {
            return when (alignment) {
                Alignment.LAWFUL_GOOD -> AlignmentCoordinates(-4, 4)
                Alignment.NEUTRAL_GOOD -> AlignmentCoordinates(0, 4)
                Alignment.CHAOTIC_GOOD -> AlignmentCoordinates(4, 4)
                Alignment.LAWFUL_NEUTRAL -> AlignmentCoordinates(-4, 0)
                Alignment.TRUE_NEUTRAL -> AlignmentCoordinates(0, 0)
                Alignment.CHAOTIC_NEUTRAL -> AlignmentCoordinates(4, 0)
                Alignment.LAWFUL_EVIL -> AlignmentCoordinates(-4, -4)
                Alignment.NEUTRAL_EVIL -> AlignmentCoordinates(0, -4)
                Alignment.CHAOTIC_EVIL -> AlignmentCoordinates(4, -4)
            }
        }
    }
}
