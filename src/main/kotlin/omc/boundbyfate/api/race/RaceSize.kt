package omc.boundbyfate.api.race

/**
 * Physical size category of a race.
 * Affects scale multiplier and future mechanics (e.g. Small cannot use heavy weapons).
 */
enum class RaceSize(val scaleMultiplier: Float) {
    TINY(0.2f),
    SMALL(0.5f),
    MEDIUM(1.0f),
    LARGE(1.5f),
    HUGE(2.0f)
}
