package omc.boundbyfate.util.math

import kotlin.math.floor

/**
 * Математические утилиты для D&D 5e механик.
 * 
 * Содержит формулы и вычисления специфичные для D&D:
 * - Вычисление модификатора характеристики
 * - Вычисление бонуса мастерства
 * - Округления по правилам D&D
 * - Вычисление DC (Difficulty Class)
 * 
 * Все формулы соответствуют официальным правилам D&D 5e.
 */
object DndMath {
    
    // ========== Модификаторы характеристик ==========
    
    /**
     * Вычисляет модификатор характеристики.
     * 
     * Формула D&D 5e: modifier = (value - 10) / 2 (округление вниз)
     * 
     * Примеры:
     * - STR 10 → +0
     * - STR 12 → +1
     * - STR 18 → +4
     * - STR 8 → -1
     * - STR 1 → -5
     * 
     * @param statValue значение характеристики (обычно 1-30)
     * @return модификатор (-5 до +10)
     */
    fun calculateModifier(statValue: Int): Int {
        return floor((statValue - 10) / 2.0).toInt()
    }
    
    /**
     * Вычисляет модификатор с учётом преимущества/помехи.
     * 
     * @param statValue значение характеристики
     * @param hasAdvantage есть ли преимущество
     * @param hasDisadvantage есть ли помеха
     * @return модификатор (преимущество/помеха не влияют на модификатор, только на броски)
     */
    fun calculateModifierWithAdvantage(
        statValue: Int,
        hasAdvantage: Boolean = false,
        hasDisadvantage: Boolean = false
    ): Int {
        // Преимущество и помеха влияют на броски d20, а не на модификатор
        return calculateModifier(statValue)
    }
    
    // ========== Бонус мастерства ==========
    
    /**
     * Вычисляет бонус мастерства по уровню персонажа.
     * 
     * Формула D&D 5e:
     * - Уровни 1-4: +2
     * - Уровни 5-8: +3
     * - Уровни 9-12: +4
     * - Уровни 13-16: +5
     * - Уровни 17-20: +6
     * 
     * @param level уровень персонажа (1-20)
     * @return бонус мастерства (+2 до +6)
     */
    fun calculateProficiencyBonus(level: Int): Int {
        require(level in 1..20) { "Level must be between 1 and 20, got $level" }
        
        return when (level) {
            in 1..4 -> 2
            in 5..8 -> 3
            in 9..12 -> 4
            in 13..16 -> 5
            in 17..20 -> 6
            else -> 2 // Fallback (не должно произойти из-за require)
        }
    }
    
    // ========== DC (Difficulty Class) ==========
    
    /**
     * Вычисляет DC заклинания.
     * 
     * Формула D&D 5e: DC = 8 + proficiency bonus + spellcasting modifier
     * 
     * @param proficiencyBonus бонус мастерства
     * @param spellcastingModifier модификатор характеристики заклинателя
     * @return DC заклинания
     */
    fun calculateSpellDC(proficiencyBonus: Int, spellcastingModifier: Int): Int {
        return 8 + proficiencyBonus + spellcastingModifier
    }
    
    /**
     * Вычисляет бонус атаки заклинанием.
     * 
     * Формула D&D 5e: attack bonus = proficiency bonus + spellcasting modifier
     * 
     * @param proficiencyBonus бонус мастерства
     * @param spellcastingModifier модификатор характеристики заклинателя
     * @return бонус атаки заклинанием
     */
    fun calculateSpellAttackBonus(proficiencyBonus: Int, spellcastingModifier: Int): Int {
        return proficiencyBonus + spellcastingModifier
    }
    
    // ========== Инициатива ==========
    
    /**
     * Вычисляет модификатор инициативы.
     * 
     * Формула D&D 5e: initiative = DEX modifier + bonuses
     * 
     * @param dexModifier модификатор ловкости
     * @param bonuses дополнительные бонусы (например, от Alert feat)
     * @return модификатор инициативы
     */
    fun calculateInitiativeModifier(dexModifier: Int, bonuses: Int = 0): Int {
        return dexModifier + bonuses
    }
    
    // ========== Конвертация D&D ↔ Minecraft ==========
    
    /**
     * Конвертирует футы D&D в блоки Minecraft.
     * 
     * В D&D:
     * - 1 фут = ~0.3048 метра
     * - 5 футов = 1 клетка на карте = ~1.5 метра
     * 
     * В Minecraft:
     * - 1 блок = 1 метр
     * 
     * Конвертация:
     * - 5 футов D&D ≈ 1.5 блока Minecraft (точно)
     * - 30 футов D&D ≈ 9 блоков Minecraft (стандартная скорость)
     * 
     * @param feet расстояние в футах D&D
     * @return расстояние в блоках Minecraft (округлено вниз)
     */
    fun feetToBlocks(feet: Int): Int {
        // 1 фут = 0.3048 метра = 0.3048 блока
        return floor(feet * 0.3048).toInt()
    }
    
    /**
     * Конвертирует футы D&D в блоки Minecraft (точное значение).
     * 
     * @param feet расстояние в футах D&D
     * @return расстояние в блоках Minecraft (double)
     */
    fun feetToBlocksExact(feet: Int): Double {
        return feet * 0.3048
    }
    
    /**
     * Конвертирует блоки Minecraft в футы D&D.
     * 
     * @param blocks расстояние в блоках Minecraft
     * @return расстояние в футах D&D (округлено вниз)
     */
    fun blocksToFeet(blocks: Int): Int {
        // 1 блок = 1 метр = 3.28084 фута
        return floor(blocks * 3.28084).toInt()
    }
    
    /**
     * Конвертирует скорость D&D (футы за раунд) в скорость Minecraft (blocks per tick).
     * 
     * В D&D:
     * - Скорость измеряется в футах за раунд (6 секунд)
     * - Стандартная скорость = 30 футов/раунд
     * 
     * В Minecraft:
     * - Скорость измеряется в blocks per tick
     * - Базовая скорость игрока = 0.1 blocks/tick
     * - 1 раунд D&D = 6 секунд = 120 тиков
     * 
     * Конвертация (30 футов/раунд):
     * - 30 футов = 9.144 блока
     * - 9.144 блока / 6 секунд = 1.524 блока/сек
     * - 1.524 блока/сек / 20 тиков = 0.0762 blocks/tick
     * 
     * Или:
     * - 9.144 блока / 120 тиков = 0.0762 blocks/tick
     * 
     * Справка:
     * - 30 футов/раунд ≈ 0.076 blocks/tick (медленнее игрока)
     * - 40 футов/раунд ≈ 0.102 blocks/tick (быстрее игрока!)
     * 
     * @param feetPerRound скорость в футах за раунд D&D
     * @return скорость в blocks per tick Minecraft
     */
    fun dndSpeedToMinecraftSpeed(feetPerRound: Int): Double {
        // 1 раунд = 6 секунд = 120 тиков
        val blocksPerRound = feetToBlocksExact(feetPerRound)
        return blocksPerRound / 120.0
    }
    
    /**
     * Конвертирует скорость Minecraft (blocks per tick) в скорость D&D (футы за раунд).
     * 
     * @param blocksPerTick скорость в blocks per tick Minecraft
     * @return скорость в футах за раунд D&D (округлено до 5)
     */
    fun minecraftSpeedToDndSpeed(blocksPerTick: Double): Int {
        // 1 раунд = 120 тиков
        val blocksPerRound = blocksPerTick * 120.0
        val feetPerRound = blocksPerRound * 3.28084
        // Округляем до ближайших 5 футов (стандарт D&D)
        return (floor(feetPerRound / 5.0) * 5).toInt()
    }
    
    /**
     * Вычисляет скорость передвижения Minecraft из скорости D&D с модификаторами.
     * 
     * Примеры:
     * - 30 футов (стандарт) → 0.0762 blocks/tick
     * - 40 футов (Monk, Wood Elf) → 0.1016 blocks/tick (быстрее игрока!)
     * - 25 футов (Dwarf, Halfling) → 0.0635 blocks/tick
     * - 20 футов (тяжёлая броня без STR) → 0.0508 blocks/tick
     * 
     * @param baseFeetPerRound базовая скорость в футах за раунд
     * @param modifierFeet модификаторы скорости в футах (могут быть отрицательными)
     * @return скорость в blocks per tick (минимум 0)
     */
    fun calculateMinecraftSpeed(baseFeetPerRound: Int, modifierFeet: Int = 0): Double {
        val totalFeet = maxOf(0, baseFeetPerRound + modifierFeet)
        return dndSpeedToMinecraftSpeed(totalFeet)
    }
    
    /**
     * Вычисляет скорость при использовании действия Dash (удвоение скорости).
     * 
     * В D&D действие Dash удваивает скорость передвижения за раунд.
     * Это тратит основное действие, поэтому нельзя атаковать.
     * 
     * Примеры:
     * - 30 футов → 60 футов (Dash) → 0.1524 blocks/tick = 3.0 блока/сек
     * - 40 футов → 80 футов (Dash) → 0.2032 blocks/tick = 4.0 блока/сек
     * 
     * Сравнение с Minecraft:
     * - Ходьба: 0.1 blocks/tick = 2.0 блока/сек
     * - Спринт: 0.13 blocks/tick = 2.6 блока/сек
     * - Dash (30 футов): 0.152 blocks/tick = 3.0 блока/сек (быстрее спринта!)
     * 
     * @param baseFeetPerRound базовая скорость в футах за раунд
     * @param modifierFeet модификаторы скорости в футах (применяются до удвоения)
     * @return скорость в blocks per tick при использовании Dash
     */
    fun calculateDashSpeed(baseFeetPerRound: Int, modifierFeet: Int = 0): Double {
        val totalFeet = maxOf(0, baseFeetPerRound + modifierFeet)
        return dndSpeedToMinecraftSpeed(totalFeet * 2)
    }
    
    /**
     * Вычисляет скорость при использовании Dash с учётом множителя.
     * 
     * Некоторые способности дают дополнительные бонусы к Dash:
     * - Rogue (Cunning Action) — Dash как бонусное действие
     * - Monk (Step of the Wind) — Dash как бонусное действие
     * - Haste (заклинание) — дополнительное действие для Dash
     * 
     * @param baseFeetPerRound базовая скорость в футах за раунд
     * @param modifierFeet модификаторы скорости в футах
     * @param dashMultiplier множитель Dash (обычно 2, но может быть больше)
     * @return скорость в blocks per tick
     */
    fun calculateDashSpeedWithMultiplier(
        baseFeetPerRound: Int,
        modifierFeet: Int = 0,
        dashMultiplier: Int = 2
    ): Double {
        val totalFeet = maxOf(0, baseFeetPerRound + modifierFeet)
        return dndSpeedToMinecraftSpeed(totalFeet * dashMultiplier)
    }
    
    // ========== Скорость передвижения (D&D) ==========
    
    /**
     * Вычисляет скорость передвижения D&D с учётом модификаторов.
     * 
     * @param baseSpeed базовая скорость в футах (обычно 30)
     * @param modifiers модификаторы скорости в футах (могут быть отрицательными)
     * @return итоговая скорость в футах (минимум 0)
     */
    fun calculateSpeed(baseSpeed: Int, modifiers: Int = 0): Int {
        return maxOf(0, baseSpeed + modifiers)
    }
    
    // ========== Округления ==========
    
    /**
     * Округляет вниз (по правилам D&D).
     * 
     * В D&D всегда округляется вниз, если не указано иное.
     */
    fun roundDown(value: Double): Int = floor(value).toInt()
    
    /**
     * Округляет вниз (float).
     */
    fun roundDown(value: Float): Int = floor(value.toDouble()).toInt()
    
    /**
     * Делит с округлением вниз.
     * 
     * Пример: 5 / 2 = 2 (не 2.5 и не 3)
     */
    fun divideRoundDown(dividend: Int, divisor: Int): Int {
        require(divisor != 0) { "Cannot divide by zero" }
        return dividend / divisor // В Kotlin целочисленное деление уже округляет вниз
    }
    
    // ========== Проверки ==========
    
    /**
     * Проверяет, является ли бросок критическим успехом.
     * 
     * @param roll результат броска d20
     * @param critRange диапазон критического успеха (обычно 20, для Champion 19-20)
     * @return true если критический успех
     */
    fun isCriticalHit(roll: Int, critRange: Int = 20): Boolean {
        return roll >= critRange
    }
    
    /**
     * Проверяет, является ли бросок критическим провалом.
     * 
     * @param roll результат броска d20
     * @return true если критический провал (всегда 1)
     */
    fun isCriticalFail(roll: Int): Boolean {
        return roll == 1
    }
    
    /**
     * Проверяет, успешна ли проверка.
     * 
     * @param roll результат броска + модификаторы
     * @param dc сложность проверки
     * @return true если проверка успешна (roll >= dc)
     */
    fun isSuccess(roll: Int, dc: Int): Boolean {
        return roll >= dc
    }
    
    // ========== Опыт и уровни ==========

    /**
     * Вычисляет требуемый опыт для достижения уровня.
     *
     * Делегирует в [omc.boundbyfate.api.level.ExperienceTable] — единственный
     * источник правды для таблицы опыта D&D 5e.
     *
     * @param level целевой уровень (1-20)
     * @return требуемый опыт
     */
    fun getXpForLevel(level: Int): Int =
        omc.boundbyfate.api.level.ExperienceTable.getRequiredExperience(level)

    /**
     * Вычисляет уровень по количеству опыта.
     *
     * Делегирует в [omc.boundbyfate.api.level.ExperienceTable].
     *
     * @param xp текущий опыт
     * @return уровень персонажа (1-20)
     */
    fun getLevelFromXp(xp: Int): Int =
        omc.boundbyfate.api.level.ExperienceTable.getLevelFromExperience(xp)
}
