package omc.boundbyfate.api.level

/**
 * Таблица опыта для прогрессии уровней D&D 5e.
 * 
 * Определяет сколько опыта нужно для достижения каждого уровня.
 * 
 * ## Таблица опыта D&D 5e
 * 
 * | Уровень | Опыт     | Бонус владения |
 * |---------|----------|----------------|
 * | 1       | 0        | +2             |
 * | 2       | 300      | +2             |
 * | 3       | 900      | +2             |
 * | 4       | 2,700    | +2             |
 * | 5       | 6,500    | +3             |
 * | 6       | 14,000   | +3             |
 * | 7       | 23,000   | +3             |
 * | 8       | 34,000   | +3             |
 * | 9       | 48,000   | +4             |
 * | 10      | 64,000   | +4             |
 * | 11      | 85,000   | +4             |
 * | 12      | 100,000  | +4             |
 * | 13      | 120,000  | +5             |
 * | 14      | 140,000  | +5             |
 * | 15      | 165,000  | +5             |
 * | 16      | 195,000  | +5             |
 * | 17      | 225,000  | +6             |
 * | 18      | 265,000  | +6             |
 * | 19      | 305,000  | +6             |
 * | 20      | 355,000  | +6             |
 */
object ExperienceTable {
    
    /**
     * Минимальный уровень.
     */
    const val MIN_LEVEL = 1
    
    /**
     * Максимальный уровень.
     */
    const val MAX_LEVEL = 20
    
    /**
     * Таблица опыта для каждого уровня.
     * 
     * Индекс = уровень - 1 (т.е. table[0] = опыт для уровня 1)
     */
    private val EXPERIENCE_TABLE = intArrayOf(
        0,       // Level 1
        300,     // Level 2
        900,     // Level 3
        2_700,   // Level 4
        6_500,   // Level 5
        14_000,  // Level 6
        23_000,  // Level 7
        34_000,  // Level 8
        48_000,  // Level 9
        64_000,  // Level 10
        85_000,  // Level 11
        100_000, // Level 12
        120_000, // Level 13
        140_000, // Level 14
        165_000, // Level 15
        195_000, // Level 16
        225_000, // Level 17
        265_000, // Level 18
        305_000, // Level 19
        355_000  // Level 20
    )
    
    /**
     * Возвращает опыт, необходимый для достижения указанного уровня.
     * 
     * @param level уровень (1-20)
     * @return необходимый опыт
     */
    fun getRequiredExperience(level: Int): Int {
        require(level in MIN_LEVEL..MAX_LEVEL) { "Level must be between $MIN_LEVEL and $MAX_LEVEL" }
        return EXPERIENCE_TABLE[level - 1]
    }
    
    /**
     * Возвращает опыт, необходимый для следующего уровня.
     * 
     * @param currentLevel текущий уровень (1-19)
     * @return необходимый опыт для следующего уровня
     */
    fun getExperienceForNextLevel(currentLevel: Int): Int {
        require(currentLevel in MIN_LEVEL until MAX_LEVEL) { 
            "Current level must be between $MIN_LEVEL and ${MAX_LEVEL - 1}" 
        }
        return EXPERIENCE_TABLE[currentLevel]
    }
    
    /**
     * Вычисляет уровень по количеству опыта.
     * 
     * @param experience количество опыта
     * @return уровень (1-20)
     */
    fun getLevelFromExperience(experience: Int): Int {
        if (experience < 0) return MIN_LEVEL
        if (experience >= EXPERIENCE_TABLE.last()) return MAX_LEVEL
        
        // Бинарный поиск
        var level = MIN_LEVEL
        for (i in EXPERIENCE_TABLE.indices) {
            if (experience >= EXPERIENCE_TABLE[i]) {
                level = i + 1
            } else {
                break
            }
        }
        return level
    }
    
    /**
     * Возвращает бонус владения для указанного уровня.
     * 
     * Формула D&D 5e: +2 на уровнях 1-4, +3 на 5-8, +4 на 9-12, +5 на 13-16, +6 на 17-20
     * 
     * @param level уровень (1-20)
     * @return бонус владения (+2 до +6)
     */
    fun getProficiencyBonus(level: Int): Int {
        require(level in MIN_LEVEL..MAX_LEVEL) { "Level must be between $MIN_LEVEL and $MAX_LEVEL" }
        return when (level) {
            in 1..4 -> 2
            in 5..8 -> 3
            in 9..12 -> 4
            in 13..16 -> 5
            in 17..20 -> 6
            else -> 2
        }
    }
    
    /**
     * Проверяет, достаточно ли опыта для повышения уровня.
     * 
     * @param currentLevel текущий уровень
     * @param currentExperience текущий опыт
     * @return true если можно повысить уровень
     */
    fun canLevelUp(currentLevel: Int, currentExperience: Int): Boolean {
        if (currentLevel >= MAX_LEVEL) return false
        return currentExperience >= getExperienceForNextLevel(currentLevel)
    }
    
    /**
     * Возвращает прогресс до следующего уровня в процентах.
     * 
     * @param currentLevel текущий уровень
     * @param currentExperience текущий опыт
     * @return прогресс (0.0 - 1.0)
     */
    fun getProgressToNextLevel(currentLevel: Int, currentExperience: Int): Float {
        if (currentLevel >= MAX_LEVEL) return 1.0f
        
        val currentLevelXp = getRequiredExperience(currentLevel)
        val nextLevelXp = getExperienceForNextLevel(currentLevel)
        val xpIntoLevel = currentExperience - currentLevelXp
        val xpNeeded = nextLevelXp - currentLevelXp
        
        return (xpIntoLevel.toFloat() / xpNeeded).coerceIn(0.0f, 1.0f)
    }
}
