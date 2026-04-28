package omc.boundbyfate.api.dice

/**
 * Результат броска кубиков.
 * 
 * Содержит полную информацию о броске:
 * - Все выпавшие значения
 * - Модификаторы
 * - Итоговый результат
 * - Строковое представление броска
 * 
 * Пример:
 * ```kotlin
 * val result = DiceRoller.roll(2, DiceType.D6, modifier = 3)
 * // rolls = [4, 5]
 * // modifier = 3
 * // total = 12
 * // expression = "2d6+3"
 * ```
 */
data class RollResult(
    /**
     * Список всех выпавших значений кубиков.
     * 
     * Для обычного броска: [roll]
     * Для преимущества/помехи: [roll1, roll2]
     * Для нескольких кубиков: [roll1, roll2, roll3, ...]
     */
    val rolls: List<Int>,
    
    /**
     * Модификатор добавленный к результату.
     * Может быть отрицательным.
     */
    val modifier: Int,
    
    /**
     * Итоговый результат броска (сумма кубиков + модификатор).
     */
    val total: Int,
    
    /**
     * Строковое представление броска.
     * Примеры: "d20+5", "2d6+3", "2d20kh1+4" (advantage)
     */
    val expression: String,
    
    /**
     * Тип преимущества/помехи (если применимо).
     */
    val advantageType: AdvantageType = AdvantageType.NONE
) {
    /**
     * Сумма всех выпавших кубиков (без модификатора).
     */
    val diceSum: Int = rolls.sum()
    
    /**
     * Минимальное выпавшее значение.
     */
    val min: Int = rolls.minOrNull() ?: 0
    
    /**
     * Максимальное выпавшее значение.
     */
    val max: Int = rolls.maxOrNull() ?: 0
    
    /**
     * Среднее значение выпавших кубиков.
     */
    val average: Double = if (rolls.isNotEmpty()) rolls.average() else 0.0
    
    /**
     * Проверяет, является ли бросок критическим успехом (натуральная 20 на d20).
     * 
     * @param critRange диапазон критического успеха (обычно 20, для Champion 19-20)
     * @return true если критический успех
     */
    fun isCriticalSuccess(critRange: Int = 20): Boolean {
        // Критический успех только на d20
        if (rolls.size != 1 && advantageType == AdvantageType.NONE) return false
        
        // Для advantage/disadvantage проверяем выбранный кубик
        val chosenRoll = when (advantageType) {
            AdvantageType.ADVANTAGE -> rolls.maxOrNull() ?: 0
            AdvantageType.DISADVANTAGE -> rolls.minOrNull() ?: 0
            AdvantageType.NONE -> rolls.firstOrNull() ?: 0
        }
        
        return chosenRoll >= critRange
    }
    
    /**
     * Проверяет, является ли бросок критическим провалом (натуральная 1 на d20).
     * 
     * @return true если критический провал
     */
    fun isCriticalFailure(): Boolean {
        // Критический провал только на d20
        if (rolls.size != 1 && advantageType == AdvantageType.NONE) return false
        
        // Для advantage/disadvantage проверяем выбранный кубик
        val chosenRoll = when (advantageType) {
            AdvantageType.ADVANTAGE -> rolls.maxOrNull() ?: 0
            AdvantageType.DISADVANTAGE -> rolls.minOrNull() ?: 0
            AdvantageType.NONE -> rolls.firstOrNull() ?: 0
        }
        
        return chosenRoll == 1
    }
    
    /**
     * Возвращает детальное строковое представление броска.
     * 
     * Пример: "2d6+3: [4, 5] + 3 = 12"
     */
    fun toDetailedString(): String {
        val rollsStr = rolls.joinToString(", ", "[", "]")
        val modStr = if (modifier != 0) {
            " ${if (modifier > 0) "+" else ""}$modifier"
        } else ""
        return "$expression: $rollsStr$modStr = $total"
    }
    
    override fun toString(): String = toDetailedString()
}
