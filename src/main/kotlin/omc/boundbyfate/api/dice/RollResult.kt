package omc.boundbyfate.api.dice

/**
 * Result of a dice roll expression.
 *
 * @property rolls Individual dice results
 * @property modifier Flat modifier added to the total
 * @property total Final value (sum of rolls + modifier)
 * @property expression Human-readable expression (e.g. "2d6+3")
 */
data class RollResult(
    val rolls: List<Int>,
    val modifier: Int,
    val total: Int,
    val expression: String
) {
    /** True if this is a natural 20 on a single d20 roll */
    val isCriticalSuccess: Boolean
        get() = rolls.size == 1 && rolls[0] == 20

    /** True if this is a natural 1 on a single d20 roll */
    val isCriticalFailure: Boolean
        get() = rolls.size == 1 && rolls[0] == 1

    /** Sum of all dice before modifier */
    val diceSum: Int get() = rolls.sum()

    override fun toString(): String {
        val rollsStr = if (rolls.size == 1) "${rolls[0]}" else "[${rolls.joinToString(", ")}]=${diceSum}"
        return if (modifier != 0) {
            val sign = if (modifier > 0) "+" else ""
            "$rollsStr$sign$modifier = $total"
        } else {
            "$rollsStr = $total"
        }
    }
}
