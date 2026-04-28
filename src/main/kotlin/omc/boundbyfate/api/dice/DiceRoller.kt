package omc.boundbyfate.api.dice

import kotlin.random.Random

/**
 * Движок для бросков кубиков.
 * 
 * Основной инструмент для всех бросков в D&D:
 * - Броски атаки (d20)
 * - Проверки навыков (d20)
 * - Спасброски (d20)
 * - Урон (любые кубики)
 * - Hit Dice (любые кубики)
 * 
 * Все методы возвращают [RollResult] с полной информацией о броске.
 * 
 * Примеры использования:
 * ```kotlin
 * // Простой бросок
 * val result = DiceRoller.roll(DiceType.D20)
 * 
 * // Урон
 * val damage = DiceRoller.roll(2, DiceType.D6, modifier = 3)
 * 
 * // Бросок с преимуществом
 * val check = DiceRoller.rollD20(AdvantageType.ADVANTAGE, modifier = 4)
 * 
 * // Парсинг строки
 * val parsed = DiceRoller.parse("2d6+3")
 * ```
 */
object DiceRoller {
    
    /**
     * Бросает один кубик.
     * 
     * @param dice тип кубика
     * @param modifier модификатор добавляемый к результату
     * @return результат броска
     */
    fun roll(dice: DiceType, modifier: Int = 0): RollResult {
        return roll(1, dice, modifier)
    }
    
    /**
     * Бросает несколько кубиков одного типа.
     * 
     * @param count количество кубиков
     * @param dice тип кубика
     * @param modifier модификатор добавляемый к сумме
     * @return результат броска со всеми выпавшими значениями
     */
    fun roll(count: Int, dice: DiceType, modifier: Int = 0): RollResult {
        require(count >= 1) { "Нужно бросить хотя бы 1 кубик" }
        
        val rolls = List(count) { Random.nextInt(1, dice.sides + 1) }
        val total = rolls.sum() + modifier
        val expression = buildExpression(count, dice, modifier)
        
        return RollResult(
            rolls = rolls,
            modifier = modifier,
            total = total,
            expression = expression,
            advantageType = AdvantageType.NONE
        )
    }
    
    /**
     * Бросает d20 с опциональным преимуществом/помехой.
     * 
     * - ADVANTAGE: бросает 2d20, берёт лучший результат
     * - DISADVANTAGE: бросает 2d20, берёт худший результат
     * - NONE: обычный бросок 1d20
     * 
     * @param advantage тип преимущества/помехи
     * @param modifier модификатор добавляемый к результату
     * @return результат броска (для advantage/disadvantage rolls содержит оба кубика)
     */
    fun rollD20(advantage: AdvantageType = AdvantageType.NONE, modifier: Int = 0): RollResult {
        return when (advantage) {
            AdvantageType.NONE -> {
                val roll = Random.nextInt(1, 21)
                val expression = buildExpression(1, DiceType.D20, modifier)
                RollResult(
                    rolls = listOf(roll),
                    modifier = modifier,
                    total = roll + modifier,
                    expression = expression,
                    advantageType = AdvantageType.NONE
                )
            }
            
            AdvantageType.ADVANTAGE -> {
                val a = Random.nextInt(1, 21)
                val b = Random.nextInt(1, 21)
                val chosen = maxOf(a, b)
                val expression = "2d20kh1" + formatModifier(modifier)
                RollResult(
                    rolls = listOf(a, b),
                    modifier = modifier,
                    total = chosen + modifier,
                    expression = expression,
                    advantageType = AdvantageType.ADVANTAGE
                )
            }
            
            AdvantageType.DISADVANTAGE -> {
                val a = Random.nextInt(1, 21)
                val b = Random.nextInt(1, 21)
                val chosen = minOf(a, b)
                val expression = "2d20kl1" + formatModifier(modifier)
                RollResult(
                    rolls = listOf(a, b),
                    modifier = modifier,
                    total = chosen + modifier,
                    expression = expression,
                    advantageType = AdvantageType.DISADVANTAGE
                )
            }
        }
    }
    
    /**
     * Парсит и бросает кубики по строковому выражению.
     * 
     * Поддерживаемые форматы:
     * - "d20"       → 1d20
     * - "2d6"       → 2d6
     * - "2d6+3"     → 2d6 с модификатором +3
     * - "1d8-1"     → 1d8 с модификатором -1
     * - "d100"      → 1d100
     * 
     * @param expression строковое выражение броска
     * @return результат броска или null если выражение некорректно
     */
    fun parse(expression: String): RollResult? {
        val cleaned = expression.trim().lowercase()
        val regex = Regex("""^(\d*)d(\d+)([+-]\d+)?$""")
        val match = regex.matchEntire(cleaned) ?: return null
        
        val count = match.groupValues[1].let { 
            if (it.isEmpty()) 1 else it.toIntOrNull() ?: return null 
        }
        val sides = match.groupValues[2].toIntOrNull() ?: return null
        val modifier = match.groupValues[3].toIntOrNull() ?: 0
        
        if (count < 1 || sides < 2) return null
        
        val rolls = List(count) { Random.nextInt(1, sides + 1) }
        val total = rolls.sum() + modifier
        
        return RollResult(
            rolls = rolls,
            modifier = modifier,
            total = total,
            expression = cleaned,
            advantageType = AdvantageType.NONE
        )
    }
    
    /**
     * Строит строковое представление броска.
     * 
     * @param count количество кубиков
     * @param dice тип кубика
     * @param modifier модификатор
     * @return строка вида "2d6+3" или "d20-1"
     */
    private fun buildExpression(count: Int, dice: DiceType, modifier: Int): String {
        val base = if (count == 1) "$dice" else "$count$dice"
        return base + formatModifier(modifier)
    }
    
    /**
     * Форматирует модификатор для строкового представления.
     * 
     * @param modifier модификатор
     * @return строка вида "+3", "-1" или "" если модификатор 0
     */
    private fun formatModifier(modifier: Int): String {
        return when {
            modifier > 0 -> "+$modifier"
            modifier < 0 -> "$modifier"
            else -> ""
        }
    }
}
