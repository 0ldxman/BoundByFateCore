package omc.boundbyfate.api.damage

import com.mojang.serialization.Codec

/**
 * Уровень сопротивления к урону в D&D 5e.
 * 
 * Система работает через числовые уровни, которые суммируются из разных источников:
 * - Расы могут давать сопротивления
 * - Классы могут давать сопротивления
 * - Заклинания могут давать временные сопротивления
 * - Предметы могут давать уязвимости
 * 
 * Итоговый уровень = сумма всех источников, ограниченная диапазоном [-3, +2].
 * 
 * Примеры:
 * ```kotlin
 * // Дварф имеет сопротивление к яду от расы
 * entity.addResistance(source = "race:dwarf", damageType = "dnd:poison", level = RESISTANCE)
 * 
 * // Надел проклятое кольцо с уязвимостью к огню
 * entity.addResistance(source = "item:cursed_ring", damageType = "dnd:fire", level = VULNERABILITY)
 * 
 * // Итоговый модификатор: -1 + 1 = 0 (NORMAL)
 * ```
 * 
 * Уровни сопротивления:
 * - **IMMUNITY** (-3): Полный иммунитет, урон = 0
 * - **STRONG_RESISTANCE** (-2): Сильное сопротивление, урон × 0.25
 * - **RESISTANCE** (-1): Сопротивление, урон × 0.5 (стандарт D&D 5e)
 * - **NORMAL** (0): Нормальный урон, урон × 1.0
 * - **VULNERABILITY** (+1): Уязвимость, урон × 2.0 (стандарт D&D 5e)
 * - **EXTREME_VULNERABILITY** (+2): Экстремальная уязвимость, урон × 4.0
 */
enum class ResistanceLevel(
    /**
     * Числовое значение уровня.
     * Используется для суммирования из разных источников.
     */
    val value: Int,
    
    /**
     * Множитель урона.
     * Итоговый урон = базовый урон × multiplier
     */
    val multiplier: Float
) {
    /**
     * Полный иммунитет к урону.
     * Урон не наносится вообще.
     * 
     * Примеры в D&D 5e:
     * - Големы иммунны к яду
     * - Призраки иммунны к некротическому урону
     * - Элементали огня иммунны к огню
     */
    IMMUNITY(-3, 0.0f),
    
    /**
     * Сильное сопротивление к урону.
     * Урон уменьшается до 25%.
     * 
     * Не стандарт D&D 5e, но полезно для модов.
     */
    STRONG_RESISTANCE(-2, 0.25f),
    
    /**
     * Сопротивление к урону.
     * Урон уменьшается вдвое.
     * 
     * Стандарт D&D 5e.
     * 
     * Примеры:
     * - Дварфы имеют сопротивление к яду
     * - Тифлинги имеют сопротивление к огню
     * - Варвары в ярости имеют сопротивление к физическому урону
     */
    RESISTANCE(-1, 0.5f),
    
    /**
     * Нормальный урон без модификаторов.
     */
    NORMAL(0, 1.0f),
    
    /**
     * Уязвимость к урону.
     * Урон удваивается.
     * 
     * Стандарт D&D 5e.
     * 
     * Примеры:
     * - Ледяные элементали уязвимы к огню
     * - Растительные существа уязвимы к огню
     * - Некоторые нежить уязвима к излучению
     */
    VULNERABILITY(1, 2.0f),
    
    /**
     * Экстремальная уязвимость к урону.
     * Урон учетверяется.
     * 
     * Не стандарт D&D 5e, но полезно для модов.
     */
    EXTREME_VULNERABILITY(2, 4.0f);
    
    /**
     * Проверяет, является ли уровень иммунитетом.
     */
    fun isImmune(): Boolean = this == IMMUNITY
    
    /**
     * Проверяет, является ли уровень сопротивлением (любым).
     */
    fun isResistance(): Boolean = value < 0 && this != IMMUNITY
    
    /**
     * Проверяет, является ли уровень уязвимостью (любой).
     */
    fun isVulnerability(): Boolean = value > 0
    
    companion object {
        /**
         * Codec для сериализации/десериализации.
         */
        val CODEC: Codec<ResistanceLevel> = Codec.STRING.xmap(
            { str -> valueOf(str.uppercase()) },
            { level -> level.name.lowercase() }
        )
        
        /**
         * Преобразует числовое значение в уровень сопротивления.
         * Автоматически ограничивает диапазон [-3, +2].
         * 
         * Примеры:
         * ```kotlin
         * fromLevel(-1) // RESISTANCE
         * fromLevel(1)  // VULNERABILITY
         * fromLevel(-5) // IMMUNITY (ограничено до -3)
         * fromLevel(10) // EXTREME_VULNERABILITY (ограничено до +2)
         * ```
         */
        fun fromLevel(level: Int): ResistanceLevel {
            val clamped = level.coerceIn(-3, 2)
            return entries.first { it.value == clamped }
        }
        
        /**
         * Комбинирует несколько уровней сопротивления.
         * Суммирует значения и ограничивает результат.
         * 
         * Примеры:
         * ```kotlin
         * // Раса даёт сопротивление, предмет даёт уязвимость
         * combine(RESISTANCE, VULNERABILITY) // NORMAL (−1 + 1 = 0)
         * 
         * // Два источника сопротивления
         * combine(RESISTANCE, RESISTANCE) // STRONG_RESISTANCE (−1 + −1 = −2)
         * 
         * // Три источника сопротивления
         * combine(RESISTANCE, RESISTANCE, RESISTANCE) // IMMUNITY (−1 + −1 + −1 = −3)
         * ```
         */
        fun combine(vararg levels: ResistanceLevel): ResistanceLevel {
            val total = levels.sumOf { it.value }
            return fromLevel(total)
        }
    }
}
