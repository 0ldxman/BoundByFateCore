package omc.boundbyfate.api.race

/**
 * Sensory capabilities granted by a race.
 * Currently stored as data - functional implementation comes later.
 *
 * @property darkvision Range in blocks (0 = none)
 * @property blindsight Range in blocks (0 = none)
 * @property tremorsense Range in blocks (0 = none)
 * @property truesight Range in blocks (0 = none)
 */
data class RaceSenses(
    val darkvision: Int = 0,
    val blindsight: Int = 0,
    val tremorsense: Int = 0,
    val truesight: Int = 0
)
