package omc.boundbyfate.api.stat

import omc.boundbyfate.util.math.DndMath

/**
 * Вычисленное значение характеристики.
 * 
 * Содержит:
 * - Базовое значение
 * - Итоговое значение (с модификаторами)
 * - Модификатор D&D (для бросков)
 * - Список применённых модификаторов
 * 
 * Пример:
 * ```kotlin
 * val strength = StatValue(
 *     base = 16,
 *     total = 20,  // 16 + 2 (раса) + 2 (ASI)
 *     modifier = 5  // (20 - 10) / 2
 * )
 * ```
 */
data class StatValue(
    /**
     * Базовое значение (из World Data).
     */
    val base: Int,
    
    /**
     * Итоговое значение (с модификаторами).
     */
    val total: Int,
    
    /**
     * Модификатор D&D для бросков.
     * Вычисляется как (total - 10) / 2
     */
    val modifier: Int,
    
    /**
     * Список применённых модификаторов.
     * Полезно для отладки и отображения в UI.
     */
    val appliedModifiers: List<StatModifier> = emptyList()
) {
    companion object {
        /**
         * Codec для сериализации StatValue.
         */
        val CODEC: com.mojang.serialization.Codec<StatValue> = 
            com.mojang.serialization.codecs.RecordCodecBuilder.create { instance ->
                instance.group(
                    com.mojang.serialization.Codec.INT.fieldOf("base").forGetter { it.base },
                    com.mojang.serialization.Codec.INT.fieldOf("total").forGetter { it.total },
                    com.mojang.serialization.Codec.INT.fieldOf("modifier").forGetter { it.modifier },
                    StatModifier.CODEC.listOf().optionalFieldOf("appliedModifiers", emptyList()).forGetter { it.appliedModifiers }
                ).apply(instance, ::StatValue)
            }
        
        /**
         * Вычисляет значение характеристики с учётом модификаторов.
         * 
         * Порядок применения:
         * 1. Проверяем OVERRIDE модификаторы (если есть, игнорируем base)
         * 2. Применяем FLAT модификаторы (складываем)
         * 3. Применяем MULTIPLY модификаторы (перемножаем)
         * 4. Применяем SET_MIN / SET_MAX (ограничиваем)
         * 
         * @param base базовое значение
         * @param modifiers список модификаторов
         * @return вычисленное значение
         */
        fun compute(base: Int, modifiers: List<StatModifier>): StatValue {
            if (modifiers.isEmpty()) {
                return StatValue(
                    base = base,
                    total = base,
                    modifier = DndMath.calculateModifier(base),
                    appliedModifiers = emptyList()
                )
            }
            
            // Сортируем по приоритету
            val sorted = modifiers.sortedBy { it.priority }
            
            // 1. Проверяем OVERRIDE
            val override = sorted
                .filter { it.type == ModifierType.OVERRIDE }
                .maxByOrNull { it.priority }
            
            if (override != null) {
                // OVERRIDE игнорирует все остальные модификаторы
                return StatValue(
                    base = base,
                    total = override.value,
                    modifier = DndMath.calculateModifier(override.value),
                    appliedModifiers = listOf(override)
                )
            }
            
            // 2. Применяем FLAT
            val flatBonus = sorted
                .filter { it.type == ModifierType.FLAT }
                .sumOf { it.value }
            
            var value = base + flatBonus
            
            // 3. Применяем MULTIPLY
            val multiplyMods = sorted.filter { it.type == ModifierType.MULTIPLY }
            for (mod in multiplyMods) {
                value = (value * mod.value / 100.0).toInt()
            }
            
            // 4. Применяем SET_MIN
            val minValue = sorted
                .filter { it.type == ModifierType.SET_MIN }
                .maxOfOrNull { it.value }
            
            if (minValue != null && value < minValue) {
                value = minValue
            }
            
            // 5. Применяем SET_MAX
            val maxValue = sorted
                .filter { it.type == ModifierType.SET_MAX }
                .minOfOrNull { it.value }
            
            if (maxValue != null && value > maxValue) {
                value = maxValue
            }
            
            return StatValue(
                base = base,
                total = value,
                modifier = DndMath.calculateModifier(value),
                appliedModifiers = sorted
            )
        }
    }
    
    /**
     * Проверяет, есть ли активные модификаторы.
     */
    fun hasModifiers(): Boolean = appliedModifiers.isNotEmpty()
    
    /**
     * Получает модификаторы определённого типа.
     */
    fun getModifiersOfType(type: ModifierType): List<StatModifier> {
        return appliedModifiers.filter { it.type == type }
    }
    
    /**
     * Получает суммарный бонус от FLAT модификаторов.
     */
    fun getFlatBonus(): Int {
        return appliedModifiers
            .filter { it.type == ModifierType.FLAT }
            .sumOf { it.value }
    }
    
    /**
     * Проверяет, переопределено ли значение.
     */
    fun isOverridden(): Boolean {
        return appliedModifiers.any { it.type == ModifierType.OVERRIDE }
    }
}
