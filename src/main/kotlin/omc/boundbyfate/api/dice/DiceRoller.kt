package omc.boundbyfate.api.dice

import kotlin.random.Random

/**
 * Core dice rolling engine. No dependencies on stats or skills.
 *
 * All methods return [RollResult] with full details of the roll.
 *
 * Usage:
 * ```kotlin
 * val result = DiceRoller.roll(DiceType.D20)
 * val damage = DiceRoller.roll(2, DiceType.D6, modifier = 3)
 * val check  = DiceRoller.rollD20(advantage = AdvantageType.ADVANTAGE, modifier = 4)
 * ```
 */
object DiceRoller {

    /**
     * Rolls a single die.
     *
     * @param dice The type of die to roll
     * @param modifier Flat modifier added to the result
     * @return RollResult with the outcome
     */
    fun roll(dice: DiceType, modifier: Int = 0): RollResult {
        return roll(1, dice, modifier)
    }

    /**
     * Rolls multiple dice of the same type.
     *
     * @param count Number of dice to roll
     * @param dice The type of die to roll
     * @param modifier Flat modifier added to the total
     * @return RollResult with all individual rolls and total
     */
    fun roll(count: Int, dice: DiceType, modifier: Int = 0): RollResult {
        require(count >= 1) { "Must roll at least 1 die" }

        val rolls = List(count) { Random.nextInt(1, dice.sides + 1) }
        val total = rolls.sum() + modifier
        val expression = buildExpression(count, dice, modifier)

        return RollResult(
            rolls = rolls,
            modifier = modifier,
            total = total,
            expression = expression
        )
    }

    /**
     * Rolls a d20 with optional advantage/disadvantage and modifier.
     *
     * With ADVANTAGE: rolls twice, takes the higher result.
     * With DISADVANTAGE: rolls twice, takes the lower result.
     *
     * @param advantage Advantage type
     * @param modifier Flat modifier added to the result
     * @return RollResult - for advantage/disadvantage, rolls contains both dice
     */
    fun rollD20(advantage: AdvantageType = AdvantageType.NONE, modifier: Int = 0): RollResult {
        return when (advantage) {
            AdvantageType.NONE -> roll(DiceType.D20, modifier)

            AdvantageType.ADVANTAGE -> {
                val a = Random.nextInt(1, 21)
                val b = Random.nextInt(1, 21)
                val chosen = maxOf(a, b)
                val expression = "2d20kh1" + if (modifier != 0) "${if (modifier > 0) "+" else ""}$modifier" else ""
                RollResult(
                    rolls = listOf(a, b),
                    modifier = modifier,
                    total = chosen + modifier,
                    expression = expression
                )
            }

            AdvantageType.DISADVANTAGE -> {
                val a = Random.nextInt(1, 21)
                val b = Random.nextInt(1, 21)
                val chosen = minOf(a, b)
                val expression = "2d20kl1" + if (modifier != 0) "${if (modifier > 0) "+" else ""}$modifier" else ""
                RollResult(
                    rolls = listOf(a, b),
                    modifier = modifier,
                    total = chosen + modifier,
                    expression = expression
                )
            }
        }
    }

    /**
     * Parses and rolls a dice expression string.
     *
     * Supported formats:
     * - "d20"       → 1d20
     * - "2d6"       → 2d6
     * - "2d6+3"     → 2d6 with +3 modifier
     * - "1d8-1"     → 1d8 with -1 modifier
     *
     * @param expression Dice expression string
     * @return RollResult or null if expression is invalid
     */
    fun parse(expression: String): RollResult? {
        val cleaned = expression.trim().lowercase()
        val regex = Regex("""^(\d*)d(\d+)([+-]\d+)?$""")
        val match = regex.matchEntire(cleaned) ?: return null

        val count = match.groupValues[1].let { if (it.isEmpty()) 1 else it.toIntOrNull() ?: return null }
        val sides = match.groupValues[2].toIntOrNull() ?: return null
        val modifier = match.groupValues[3].toIntOrNull() ?: 0

        if (count < 1 || sides < 2) return null

        val rolls = List(count) { Random.nextInt(1, sides + 1) }
        val total = rolls.sum() + modifier

        return RollResult(
            rolls = rolls,
            modifier = modifier,
            total = total,
            expression = cleaned
        )
    }

    private fun buildExpression(count: Int, dice: DiceType, modifier: Int): String {
        val base = if (count == 1) "$dice" else "$count$dice"
        return when {
            modifier > 0 -> "$base+$modifier"
            modifier < 0 -> "$base$modifier"
            else -> base
        }
    }
}
