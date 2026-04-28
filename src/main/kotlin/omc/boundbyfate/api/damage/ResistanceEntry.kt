package omc.boundbyfate.api.damage

import net.minecraft.util.Identifier

/**
 * Запись о сопротивлении — описывает к чему именно есть сопротивление.
 * 
 * Может быть:
 * - К конкретному типу урона (fire, slashing, poison)
 * - К источнику урона (все магическое, все физическое, все environmental)
 * - К комбинации (немагический физический урон)
 * 
 * ## Примеры
 * 
 * ### Сопротивление к конкретному типу
 * ```kotlin
 * // Дварф: сопротивление к яду
 * ResistanceEntry.DamageType(
 *     damageType = Identifier("dnd", "poison"),
 *     level = ResistanceLevel.RESISTANCE
 * )
 * 
 * // Тифлинг: сопротивление к огню
 * ResistanceEntry.DamageType(
 *     damageType = Identifier("dnd", "fire"),
 *     level = ResistanceLevel.RESISTANCE
 * )
 * ```
 * 
 * ### Сопротивление к источнику
 * ```kotlin
 * // Голем: иммунитет ко всему урону от окружения
 * ResistanceEntry.Source(
 *     source = DamageSource.ENVIRONMENTAL,
 *     level = ResistanceLevel.IMMUNITY
 * )
 * 
 * // Призрак: сопротивление ко всему немагическому урону
 * ResistanceEntry.NonMagical(
 *     level = ResistanceLevel.RESISTANCE
 * )
 * ```
 * 
 * ### Комбинированное сопротивление
 * ```kotlin
 * // Оборотень: иммунитет к немагическому физическому урону
 * ResistanceEntry.Conditional(
 *     level = ResistanceLevel.IMMUNITY,
 *     condition = { damage ->
 *         damage.isPhysical() && damage.hasPhysicalType()
 *     }
 * )
 * ```
 */
sealed class ResistanceEntry {
    abstract val level: ResistanceLevel
    
    /**
     * Проверяет, применяется ли это сопротивление к данному урону.
     */
    abstract fun matches(damage: DamageInstance): Boolean
    
    /**
     * Сопротивление к конкретному типу урона.
     * 
     * Примеры:
     * - Сопротивление к огню
     * - Иммунитет к яду
     * - Уязвимость к холоду
     */
    data class DamageType(
        val damageType: Identifier,
        override val level: ResistanceLevel
    ) : ResistanceEntry() {
        override fun matches(damage: DamageInstance): Boolean {
            return damage.type == damageType
        }
    }
    
    /**
     * Сопротивление к источнику урона.
     * 
     * Примеры:
     * - Иммунитет ко всему урону от окружения
     * - Сопротивление ко всему магическому урону
     * - Уязвимость ко всему физическому урону
     */
    data class Source(
        val source: DamageSource,
        override val level: ResistanceLevel
    ) : ResistanceEntry() {
        override fun matches(damage: DamageInstance): Boolean {
            return damage.source == source
        }
    }
    
    /**
     * Сопротивление ко всему немагическому урону.
     * 
     * Удобный shortcut для Source(PHYSICAL) + Source(ENVIRONMENTAL).
     * 
     * Примеры:
     * - Призрак: сопротивление ко всему немагическому
     * - Спектр: иммунитет ко всему немагическому
     */
    data class NonMagical(
        override val level: ResistanceLevel
    ) : ResistanceEntry() {
        override fun matches(damage: DamageInstance): Boolean {
            return !damage.isMagical()
        }
    }
    
    /**
     * Сопротивление ко всему магическому урону.
     * 
     * Удобный shortcut для Source(MAGICAL).
     * 
     * Примеры:
     * - Антимаг: сопротивление ко всему магическому
     * - Голем антимагии: иммунитет ко всему магическому
     */
    data class Magical(
        override val level: ResistanceLevel
    ) : ResistanceEntry() {
        override fun matches(damage: DamageInstance): Boolean {
            return damage.isMagical()
        }
    }
    
    /**
     * Условное сопротивление с кастомной логикой.
     * 
     * Примеры:
     * - Оборотень: иммунитет к немагическому физическому урону
     * - Элементаль: иммунитет к своему элементу + сопротивление к другим
     */
    data class Conditional(
        override val level: ResistanceLevel,
        val condition: (DamageInstance) -> Boolean
    ) : ResistanceEntry() {
        override fun matches(damage: DamageInstance): Boolean {
            return condition(damage)
        }
        
        // Для data class нужно переопределить equals/hashCode
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Conditional) return false
            return level == other.level
        }
        
        override fun hashCode(): Int {
            return level.hashCode()
        }
    }
    
    /**
     * Комбинация нескольких условий (AND).
     * 
     * Примеры:
     * - Сопротивление к немагическому физическому урону
     * - Иммунитет к магическому огню
     */
    data class Combined(
        override val level: ResistanceLevel,
        val entries: List<ResistanceEntry>
    ) : ResistanceEntry() {
        override fun matches(damage: DamageInstance): Boolean {
            return entries.all { it.matches(damage) }
        }
    }
}
