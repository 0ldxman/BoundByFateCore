package omc.boundbyfate.api.dice

/**
 * Тип преимущества/помехи для бросков d20.
 * 
 * В D&D 5e:
 * - ADVANTAGE — бросаешь 2d20, берёшь лучший результат
 * - DISADVANTAGE — бросаешь 2d20, берёшь худший результат
 * - NONE — обычный бросок 1d20
 * 
 * Правила:
 * - Если есть и преимущество и помеха одновременно — они отменяют друг друга (NONE)
 * - Несколько источников преимущества не складываются (всё равно ADVANTAGE)
 * - Несколько источников помехи не складываются (всё равно DISADVANTAGE)
 */
enum class AdvantageType {
    /**
     * Обычный бросок (1d20).
     */
    NONE,
    
    /**
     * Преимущество (2d20, берём лучший).
     * 
     * Источники:
     * - Помощь союзника
     * - Скрытая атака
     * - Цель ослеплена/парализована/оглушена
     * - Заклинания (Bless, Guidance)
     */
    ADVANTAGE,
    
    /**
     * Помеха (2d20, берём худший).
     * 
     * Источники:
     * - Атакующий ослеплён
     * - Цель в укрытии
     * - Атака с большого расстояния
     * - Состояния (Poisoned, Frightened)
     */
    DISADVANTAGE;
    
    companion object {
        /**
         * Комбинирует несколько типов преимущества/помехи.
         * 
         * Правила D&D 5e:
         * - Если есть хотя бы одно преимущество и хотя бы одна помеха → NONE
         * - Если есть только преимущества → ADVANTAGE
         * - Если есть только помехи → DISADVANTAGE
         * - Если ничего нет → NONE
         * 
         * @param types список типов для комбинирования
         * @return итоговый тип
         */
        fun combine(vararg types: AdvantageType): AdvantageType {
            val hasAdvantage = types.any { it == ADVANTAGE }
            val hasDisadvantage = types.any { it == DISADVANTAGE }
            
            return when {
                hasAdvantage && hasDisadvantage -> NONE  // Отменяют друг друга
                hasAdvantage -> ADVANTAGE
                hasDisadvantage -> DISADVANTAGE
                else -> NONE
            }
        }
        
        /**
         * Комбинирует список типов.
         */
        fun combine(types: List<AdvantageType>): AdvantageType {
            return combine(*types.toTypedArray())
        }
    }
    
    /**
     * Проверяет, есть ли преимущество.
     */
    fun hasAdvantage(): Boolean = this == ADVANTAGE
    
    /**
     * Проверяет, есть ли помеха.
     */
    fun hasDisadvantage(): Boolean = this == DISADVANTAGE
    
    /**
     * Проверяет, обычный ли бросок.
     */
    fun isNormal(): Boolean = this == NONE
}
