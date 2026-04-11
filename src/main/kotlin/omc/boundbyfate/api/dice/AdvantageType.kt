package omc.boundbyfate.api.dice

/**
 * Advantage/disadvantage state for d20 rolls.
 */
enum class AdvantageType {
    /** Roll once normally */
    NONE,

    /** Roll twice, take the higher result */
    ADVANTAGE,

    /** Roll twice, take the lower result */
    DISADVANTAGE
}
