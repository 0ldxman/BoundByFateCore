package omc.boundbyfate.api.skill

/**
 * Уровень владения навыком.
 * 
 * В D&D 5e:
 * - NONE — нет владения (только модификатор характеристики)
 * - PROFICIENT — владение (+ бонус мастерства)
 * - EXPERTISE — экспертиза (+ удвоенный бонус мастерства)
 * 
 * Формулы:
 * ```
 * NONE:       d20 + stat_modifier
 * PROFICIENT: d20 + stat_modifier + proficiency_bonus
 * EXPERTISE:  d20 + stat_modifier + (proficiency_bonus * 2)
 * ```
 * 
 * Примеры:
 * - Разбойник имеет PROFICIENT в Stealth
 * - Разбойник с Expertise имеет EXPERTISE в Stealth
 * - Воин не имеет владения Stealth (NONE)
 */
enum class ProficiencyLevel(val multiplier: Double) {
    /**
     * Нет владения.
     * Используется только модификатор характеристики.
     */
    NONE(0.0),
    
    /**
     * Владение навыком.
     * Добавляется бонус мастерства.
     */
    PROFICIENT(1.0),
    
    /**
     * Экспертиза (Expertise).
     * Добавляется удвоенный бонус мастерства.
     * 
     * Источники:
     * - Разбойник (Rogue) — 2 навыка на 1 уровне, ещё 2 на 6 уровне
     * - Бард (Bard) — 2 навыка на 3 уровне, ещё 2 на 10 уровне
     * - Фит Prodigy — 1 навык
     */
    EXPERTISE(2.0);
    
    /**
     * Проверяет, есть ли владение (PROFICIENT или EXPERTISE).
     */
    fun hasProficiency(): Boolean = this != NONE
    
    /**
     * Проверяет, есть ли экспертиза.
     */
    fun hasExpertise(): Boolean = this == EXPERTISE
    
    /**
     * Вычисляет бонус с учётом уровня владения.
     * 
     * @param proficiencyBonus базовый бонус мастерства
     * @return итоговый бонус
     */
    fun calculateBonus(proficiencyBonus: Int): Int {
        return (proficiencyBonus * multiplier).toInt()
    }
    
    companion object {
        /**
         * Повышает уровень владения.
         * 
         * NONE → PROFICIENT → EXPERTISE
         * 
         * @param current текущий уровень
         * @return следующий уровень или текущий если уже максимум
         */
        fun upgrade(current: ProficiencyLevel): ProficiencyLevel {
            return when (current) {
                NONE -> PROFICIENT
                PROFICIENT -> EXPERTISE
                EXPERTISE -> EXPERTISE  // Уже максимум
            }
        }
        
        /**
         * Понижает уровень владения.
         * 
         * EXPERTISE → PROFICIENT → NONE
         * 
         * @param current текущий уровень
         * @return предыдущий уровень или текущий если уже минимум
         */
        fun downgrade(current: ProficiencyLevel): ProficiencyLevel {
            return when (current) {
                EXPERTISE -> PROFICIENT
                PROFICIENT -> NONE
                NONE -> NONE  // Уже минимум
            }
        }
    }
}
