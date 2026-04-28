package omc.boundbyfate.api.ability

import com.mojang.serialization.Codec
import omc.boundbyfate.api.dice.DiceRoller
import omc.boundbyfate.api.dice.DiceType

/**
 * Строковое выражение броска кубиков.
 *
 * Хранит выражение как строку ("8d6", "1d10+3", "2d4") и умеет его бросать.
 * Намеренно не парсится при создании — парсинг происходит при броске.
 *
 * ## Примеры
 *
 * ```kotlin
 * val expr = DiceExpression.of(8, DiceType.D6)       // "8d6"
 * val expr = DiceExpression.parse("2d6+3")            // "2d6+3"
 * val result = expr.roll()                            // RollResult
 * val total = expr.rollTotal()                        // Int
 * ```
 *
 * ## JSON
 *
 * Хранится как строка: `"damage_dice": "8d6"`
 */
@JvmInline
value class DiceExpression(val expression: String) {

    /**
     * Бросает кубики и возвращает полный результат.
     */
    fun roll() = DiceRoller.parse(expression)
        ?: error("Invalid dice expression: '$expression'")

    /**
     * Бросает кубики и возвращает только итоговое число.
     */
    fun rollTotal(): Int = roll().total

    /**
     * Добавляет кубики к выражению.
     * Например: "8d6" + 2 кубика d6 = "10d6"
     */
    fun addDice(count: Int, dice: DiceType): DiceExpression {
        val current = DiceRoller.parse(expression)
            ?: return DiceExpression("${count}${dice}")
        // Простое добавление — пересобираем выражение
        val newCount = current.rolls.size + count
        return DiceExpression("${newCount}${dice}")
    }

    override fun toString(): String = expression

    companion object {
        val CODEC: Codec<DiceExpression> = Codec.STRING.xmap(
            { DiceExpression(it) },
            { it.expression }
        )

        /**
         * Создаёт выражение из количества и типа кубика.
         */
        fun of(count: Int, dice: DiceType): DiceExpression =
            DiceExpression("${count}${dice}")

        /**
         * Парсит строку в выражение. Возвращает null если некорректно.
         */
        fun parse(str: String): DiceExpression? {
            // Проверяем что строка вообще парсится
            return if (DiceRoller.parse(str) != null) DiceExpression(str) else null
        }
    }
}
