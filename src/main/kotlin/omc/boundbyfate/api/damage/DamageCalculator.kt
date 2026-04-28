package omc.boundbyfate.api.damage

import net.minecraft.util.Identifier

/**
 * Калькулятор урона с учётом сопротивлений.
 * 
 * Вычисляет итоговый урон на основе:
 * 1. Экземпляра урона (DamageInstance)
 * 2. Сопротивлений/иммунитетов/уязвимостей цели
 * 3. Условных сопротивлений (зависящих от источника)
 * 
 * Примеры:
 * ```kotlin
 * // Базовый урон с сопротивлением
 * val damage = DamageInstance(Identifier("dnd", "fire"), 20f, isMagical = true)
 * val resistances = mapOf(Identifier("dnd", "fire") to ResistanceLevel.RESISTANCE)
 * val final = DamageCalculator.calculate(damage, resistances)
 * // Результат: 10f (20 × 0.5)
 * 
 * // Оборотень: иммунитет к немагическому физическому урону
 * val normalSword = DamageInstance(Identifier("dnd", "slashing"), 10f, isMagical = false)
 * val magicSword = DamageInstance(Identifier("dnd", "slashing"), 10f, isMagical = true)
 * 
 * val werewolfResistances = mapOf(
 *     Identifier("dnd", "slashing") to ResistanceLevel.IMMUNITY
 * )
 * val werewolfConditions: (DamageInstance) -> Boolean = { damage ->
 *     !damage.isMagical && damage.isPhysical()
 * }
 * 
 * calculate(normalSword, werewolfResistances, werewolfConditions) // 0f (иммунитет)
 * calculate(magicSword, werewolfResistances, werewolfConditions)  // 10f (условие не выполнено)
 * ```
 */
object DamageCalculator {
    
    /**
     * Вычисляет итоговый урон с учётом сопротивлений.
     * 
     * @param damage Экземпляр урона
     * @param resistances Карта сопротивлений цели (тип урона -> уровень)
     * @param condition Условие применения сопротивления (опционально)
     * @return Итоговый урон после применения модификаторов
     */
    fun calculate(
        damage: DamageInstance,
        resistances: Map<Identifier, ResistanceLevel>,
        condition: ((DamageInstance) -> Boolean)? = null
    ): Float {
        if (damage.amount <= 0f) return 0f
        
        val resistanceLevel = resistances[damage.type] ?: ResistanceLevel.NORMAL
        
        // Если есть условие и оно не выполнено, сопротивление не применяется
        if (condition != null && !condition(damage)) {
            return damage.amount
        }
        
        return damage.amount * resistanceLevel.multiplier
    }
    
    /**
     * Вычисляет итоговый урон с учётом сопротивлений из нескольких источников.
     * 
     * @param damage Экземпляр урона
     * @param sources Карта источников сопротивлений (источник -> (тип урона -> уровень))
     * @param condition Условие применения сопротивления (опционально)
     * @return Итоговый урон после применения модификаторов
     */
    fun calculateWithSources(
        damage: DamageInstance,
        sources: Map<Identifier, Map<Identifier, Int>>,
        condition: ((DamageInstance) -> Boolean)? = null
    ): Float {
        if (damage.amount <= 0f) return 0f
        
        // Если есть условие и оно не выполнено, сопротивление не применяется
        if (condition != null && !condition(damage)) {
            return damage.amount
        }
        
        // Суммируем все уровни сопротивления из разных источников
        var totalLevel = 0
        for ((_, contributions) in sources) {
            totalLevel += contributions[damage.type] ?: 0
        }
        
        val resistanceLevel = ResistanceLevel.fromLevel(totalLevel)
        return damage.amount * resistanceLevel.multiplier
    }
    
    /**
     * Вычисляет урон от нескольких экземпляров (например, Flame Tongue).
     * 
     * @param damages Список экземпляров урона
     * @param resistances Карта сопротивлений цели
     * @param condition Условие применения сопротивления (опционально)
     * @return Итоговый урон после применения модификаторов ко всем экземплярам
     */
    fun calculateMultiple(
        damages: List<DamageInstance>,
        resistances: Map<Identifier, ResistanceLevel>,
        condition: ((DamageInstance) -> Boolean)? = null
    ): Float {
        return damages.sumOf { damage ->
            calculate(damage, resistances, condition).toDouble()
        }.toFloat()
    }
    
    /**
     * Вычисляет урон от нескольких экземпляров с источниками.
     * 
     * @param damages Список экземпляров урона
     * @param sources Карта источников сопротивлений
     * @param condition Условие применения сопротивления (опционально)
     * @return Итоговый урон после применения модификаторов ко всем экземплярам
     */
    fun calculateMultipleWithSources(
        damages: List<DamageInstance>,
        sources: Map<Identifier, Map<Identifier, Int>>,
        condition: ((DamageInstance) -> Boolean)? = null
    ): Float {
        return damages.sumOf { damage ->
            calculateWithSources(damage, sources, condition).toDouble()
        }.toFloat()
    }
    
    /**
     * Проверяет, имеет ли цель иммунитет к данному урону.
     * 
     * @param damage Экземпляр урона
     * @param resistances Карта сопротивлений цели
     * @param condition Условие применения сопротивления (опционально)
     * @return true если цель имеет иммунитет
     */
    fun isImmune(
        damage: DamageInstance,
        resistances: Map<Identifier, ResistanceLevel>,
        condition: ((DamageInstance) -> Boolean)? = null
    ): Boolean {
        if (condition != null && !condition(damage)) {
            return false
        }
        return resistances[damage.type]?.isImmune() ?: false
    }
    
    /**
     * Проверяет, имеет ли цель иммунитет к данному урону (версия с источниками).
     * 
     * @param damage Экземпляр урона
     * @param sources Карта источников сопротивлений
     * @param condition Условие применения сопротивления (опционально)
     * @return true если цель имеет иммунитет
     */
    fun isImmuneWithSources(
        damage: DamageInstance,
        sources: Map<Identifier, Map<Identifier, Int>>,
        condition: ((DamageInstance) -> Boolean)? = null
    ): Boolean {
        if (condition != null && !condition(damage)) {
            return false
        }
        
        var totalLevel = 0
        for ((_, contributions) in sources) {
            totalLevel += contributions[damage.type] ?: 0
        }
        
        return ResistanceLevel.fromLevel(totalLevel).isImmune()
    }
    
    /**
     * Получает эффективный уровень сопротивления для урона.
     * 
     * @param damage Экземпляр урона
     * @param sources Карта источников сопротивлений
     * @param condition Условие применения сопротивления (опционально)
     * @return Эффективный уровень сопротивления
     */
    fun getEffectiveResistance(
        damage: DamageInstance,
        sources: Map<Identifier, Map<Identifier, Int>>,
        condition: ((DamageInstance) -> Boolean)? = null
    ): ResistanceLevel {
        if (condition != null && !condition(damage)) {
            return ResistanceLevel.NORMAL
        }
        
        var totalLevel = 0
        for ((_, contributions) in sources) {
            totalLevel += contributions[damage.type] ?: 0
        }
        
        return ResistanceLevel.fromLevel(totalLevel)
    }
}
