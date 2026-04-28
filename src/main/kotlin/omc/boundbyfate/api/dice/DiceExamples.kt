package omc.boundbyfate.api.dice

/**
 * Примеры использования системы кубиков.
 * 
 * Этот файл демонстрирует различные способы использования DiceRoller
 * для типичных ситуаций в D&D 5e.
 */
object DiceExamples {
    
    /**
     * Пример 1: Простой бросок атаки.
     * 
     * Воин атакует мечом: d20 + модификатор Силы (3) + бонус мастерства (2)
     */
    fun attackRoll() {
        val modifier = 3 + 2  // Сила + мастерство
        val result = DiceRoller.rollD20(modifier = modifier)
        
        println("Бросок атаки: ${result.toDetailedString()}")
        // Вывод: "d20+5: [14] +5 = 19"
        
        if (result.isCriticalSuccess()) {
            println("Критический успех! Удваиваем кубики урона!")
        } else if (result.isCriticalFailure()) {
            println("Критический провал! Промах!")
        }
    }
    
    /**
     * Пример 2: Бросок с преимуществом.
     * 
     * Разбойник атакует из скрытности (преимущество).
     */
    fun attackWithAdvantage() {
        val modifier = 4 + 3  // Ловкость + мастерство
        val result = DiceRoller.rollD20(AdvantageType.ADVANTAGE, modifier)
        
        println("Бросок с преимуществом: ${result.toDetailedString()}")
        // Вывод: "2d20kh1+7: [12, 18] +7 = 25"
        // kh1 = keep highest 1 (берём лучший из двух)
    }
    
    /**
     * Пример 3: Бросок с помехой.
     * 
     * Маг атакует в ближнем бою (помеха для заклинаний).
     */
    fun attackWithDisadvantage() {
        val modifier = 2 + 2  // Интеллект + мастерство
        val result = DiceRoller.rollD20(AdvantageType.DISADVANTAGE, modifier)
        
        println("Бросок с помехой: ${result.toDetailedString()}")
        // Вывод: "2d20kl1+4: [15, 8] +4 = 12"
        // kl1 = keep lowest 1 (берём худший из двух)
    }
    
    /**
     * Пример 4: Урон от оружия.
     * 
     * Длинный меч: 1d8 + модификатор Силы (3)
     */
    fun weaponDamage() {
        val strModifier = 3
        val result = DiceRoller.roll(DiceType.D8, modifier = strModifier)
        
        println("Урон длинным мечом: ${result.toDetailedString()}")
        // Вывод: "d8+3: [6] +3 = 9"
    }
    
    /**
     * Пример 5: Критический урон.
     * 
     * При критическом успехе удваиваем кубики урона (но не модификатор!).
     */
    fun criticalDamage() {
        val strModifier = 3
        
        // Обычный урон: 1d8+3
        val normalDamage = DiceRoller.roll(DiceType.D8, modifier = strModifier)
        println("Обычный урон: ${normalDamage.toDetailedString()}")
        
        // Критический урон: 2d8+3 (удваиваем кубики)
        val critDamage = DiceRoller.roll(2, DiceType.D8, modifier = strModifier)
        println("Критический урон: ${critDamage.toDetailedString()}")
        // Вывод: "2d8+3: [5, 7] +3 = 15"
    }
    
    /**
     * Пример 6: Урон заклинанием.
     * 
     * Огненный шар (Fireball): 8d6 урона огнём.
     */
    fun spellDamage() {
        val result = DiceRoller.roll(8, DiceType.D6)
        
        println("Урон Огненным шаром: ${result.toDetailedString()}")
        // Вывод: "8d6: [3, 5, 2, 6, 4, 1, 5, 4] = 30"
        
        println("Минимум: ${result.min}, Максимум: ${result.max}, Среднее: ${"%.1f".format(result.average)}")
    }
    
    /**
     * Пример 7: Проверка навыка.
     * 
     * Проверка Скрытности: d20 + модификатор Ловкости (4) + мастерство (2)
     */
    fun skillCheck() {
        val modifier = 4 + 2  // Ловкость + мастерство
        val result = DiceRoller.rollD20(modifier = modifier)
        
        val dc = 15  // Сложность проверки (Difficulty Class)
        
        println("Проверка Скрытности: ${result.toDetailedString()}")
        if (result.total >= dc) {
            println("Успех! (DC $dc)")
        } else {
            println("Провал! (DC $dc)")
        }
    }
    
    /**
     * Пример 8: Спасбросок.
     * 
     * Спасбросок Ловкости против Огненного шара.
     */
    fun savingThrow() {
        val dexModifier = 3
        val result = DiceRoller.rollD20(modifier = dexModifier)
        
        val spellDC = 15  // DC заклинания
        
        println("Спасбросок Ловкости: ${result.toDetailedString()}")
        if (result.total >= spellDC) {
            println("Успех! Получаете половину урона")
        } else {
            println("Провал! Получаете полный урон")
        }
    }
    
    /**
     * Пример 9: Hit Dice (восстановление HP).
     * 
     * Воин (Hit Die d10) тратит Hit Die во время короткого отдыха.
     */
    fun hitDiceHealing() {
        val conModifier = 2  // Модификатор Телосложения
        val result = DiceRoller.roll(DiceType.D10, modifier = conModifier)
        
        println("Восстановление HP: ${result.toDetailedString()}")
        println("Восстановлено ${result.total} HP")
    }
    
    /**
     * Пример 10: Парсинг строковых выражений.
     * 
     * Полезно для конфигов и команд.
     */
    fun parseExpressions() {
        val expressions = listOf(
            "d20",      // 1d20
            "2d6+3",    // 2d6 с модификатором +3
            "1d8-1",    // 1d8 с модификатором -1
            "4d6",      // 4d6 (для генерации характеристик)
            "d100"      // 1d100 (процентный бросок)
        )
        
        expressions.forEach { expr ->
            val result = DiceRoller.parse(expr)
            if (result != null) {
                println("$expr → ${result.toDetailedString()}")
            } else {
                println("$expr → Ошибка парсинга")
            }
        }
    }
    
    /**
     * Пример 11: Генерация характеристик персонажа.
     * 
     * Метод 4d6 drop lowest: бросаем 4d6, отбрасываем наименьший.
     */
    fun generateAbilityScore() {
        val rolls = List(4) { DiceRoller.roll(DiceType.D6) }
        val values = rolls.map { it.total }
        val dropped = values.minOrNull() ?: 0
        val total = values.sum() - dropped
        
        println("Генерация характеристики: ${values.joinToString(", ")}")
        println("Отбрасываем: $dropped")
        println("Итого: $total")
    }
    
    /**
     * Пример 12: Комбинирование преимущества и помехи.
     * 
     * Если есть и преимущество и помеха — они отменяют друг друга.
     */
    fun advantageDisadvantageCombination() {
        // Источники преимущества: скрытность, помощь союзника
        // Источники помехи: цель в укрытии
        val combined = AdvantageType.combine(
            AdvantageType.ADVANTAGE,
            AdvantageType.ADVANTAGE,  // Несколько источников не складываются
            AdvantageType.DISADVANTAGE
        )
        
        println("Итоговый тип: $combined")  // NONE (отменяют друг друга)
        
        val result = DiceRoller.rollD20(combined, modifier = 5)
        println("Бросок: ${result.toDetailedString()}")
    }
}
