package omc.boundbyfate.api.damage

import net.minecraft.util.Identifier

/**
 * Экземпляр урона — конкретный урон, наносимый в момент атаки.
 * 
 * Описывает:
 * - **Тип урона** (fire, slashing, poison) — ЧТО за урон
 * - **Количество** — сколько урона
 * - **Источник** (physical/magical/environmental) — КАК он был нанесён
 * 
 * ## Примеры
 * 
 * ### Обычное оружие
 * ```kotlin
 * // Обычный кинжал: 1d4 колющего (физический)
 * val damage = DamageInstance(
 *     type = Identifier("dnd", "piercing"),
 *     amount = 4f,
 *     source = DamageSource.PHYSICAL
 * )
 * ```
 * 
 * ### Магическое оружие
 * ```kotlin
 * // Магический меч +1: 1d8+1 рубящего (магический)
 * val damage = DamageInstance(
 *     type = Identifier("dnd", "slashing"),
 *     amount = 9f,
 *     source = DamageSource.MAGICAL
 * )
 * ```
 * 
 * ### Оружие с несколькими типами урона
 * ```kotlin
 * // Flame Tongue: 1d8 рубящего (магический) + 2d6 огненного (магический)
 * val damages = listOf(
 *     DamageInstance(Identifier("dnd", "slashing"), 8f, source = DamageSource.MAGICAL),
 *     DamageInstance(Identifier("dnd", "fire"), 7f, source = DamageSource.MAGICAL)
 * )
 * 
 * // Кинжал в горючей смеси: 1d4 колющего (физический) + 1d4 огня (окружение)
 * val damages = listOf(
 *     DamageInstance(Identifier("dnd", "piercing"), 4f, source = DamageSource.PHYSICAL),
 *     DamageInstance(Identifier("dnd", "fire"), 4f, source = DamageSource.ENVIRONMENTAL)
 * )
 * ```
 * 
 * ### Заклинания
 * ```kotlin
 * // Fireball: 8d6 огненного (магический)
 * val damage = DamageInstance(
 *     type = Identifier("dnd", "fire"),
 *     amount = 28f,
 *     source = DamageSource.MAGICAL
 * )
 * 
 * // Magic Missile: 1d4+1 силового (магический)
 * val damage = DamageInstance(
 *     type = Identifier("dnd", "force"),
 *     amount = 4f,
 *     source = DamageSource.MAGICAL
 * )
 * ```
 * 
 * ### Окружение
 * ```kotlin
 * // Горящая бочка: 2d6 огненного (окружение)
 * val damage = DamageInstance(
 *     type = Identifier("dnd", "fire"),
 *     amount = 7f,
 *     source = DamageSource.ENVIRONMENTAL
 * )
 * 
 * // Падение: 3d6 дробящего (окружение)
 * val damage = DamageInstance(
 *     type = Identifier("dnd", "bludgeoning"),
 *     amount = 10f,
 *     source = DamageSource.ENVIRONMENTAL
 * )
 * ```
 * 
 * ## Применение с сопротивлениями
 * 
 * ### Обычное сопротивление
 * ```kotlin
 * // Дварф с сопротивлением к яду
 * val damage = DamageInstance(
 *     Identifier("dnd", "poison"), 
 *     20f, 
 *     source = DamageSource.PHYSICAL
 * )
 * 
 * // Проверяем сопротивление только по типу урона
 * val resistance = target.getResistance(damage.type)
 * val finalDamage = damage.amount * resistance.multiplier
 * // Результат: 10f (20 × 0.5)
 * ```
 * 
 * ### Условное сопротивление (зависит от источника)
 * ```kotlin
 * // Оборотень: иммунитет к немагическому физическому урону
 * 
 * // Обычный меч
 * val normalSword = DamageInstance(
 *     Identifier("dnd", "slashing"), 
 *     10f, 
 *     source = DamageSource.PHYSICAL
 * )
 * if (normalSword.source != DamageSource.MAGICAL && normalSword.isPhysical()) {
 *     // Иммунитет! Урон = 0
 * }
 * 
 * // Магический меч
 * val magicSword = DamageInstance(
 *     Identifier("dnd", "slashing"), 
 *     10f, 
 *     source = DamageSource.MAGICAL
 * )
 * if (magicSword.source != DamageSource.MAGICAL && magicSword.isPhysical()) {
 *     // Условие не выполнено, урон проходит
 * }
 * // Результат: 10f
 * ```
 * 
 * ### Голем: иммунитет к яду и психическому урону
 * ```kotlin
 * val poisonDamage = DamageInstance(
 *     Identifier("dnd", "poison"), 
 *     15f, 
 *     source = DamageSource.MAGICAL
 * )
 * val psychicDamage = DamageInstance(
 *     Identifier("dnd", "psychic"), 
 *     12f, 
 *     source = DamageSource.MAGICAL
 * )
 * 
 * // Иммунитет не зависит от источника (physical/magical/environmental)
 * // Проверяем только тип урона
 * val poisonResistance = golem.getResistance(poisonDamage.type) // IMMUNITY
 * val psychicResistance = golem.getResistance(psychicDamage.type) // IMMUNITY
 * 
 * // Оба урона = 0
 * ```
 */
data class DamageInstance(
    /**
     * Тип урона (fire, slashing, poison, etc.)
     */
    val type: Identifier,
    
    /**
     * Количество урона (до применения сопротивлений).
     */
    val amount: Float,
    
    /**
     * Источник урона (физический/магический/окружение).
     * 
     * - `PHYSICAL` — физический урон от обычного оружия
     * - `MAGICAL` — магический урон от заклинаний и магического оружия
     * - `ENVIRONMENTAL` — урон от окружения (падение, лава, ловушки)
     * 
     * Влияет на взаимодействие с существами, имеющими:
     * - "Сопротивление к немагическому урону" (проверяет source != MAGICAL)
     * - "Иммунитет к урону от окружения" (проверяет source == ENVIRONMENTAL)
     * - "Уязвимость к магическому урону" (проверяет source == MAGICAL)
     */
    val source: DamageSource = DamageSource.PHYSICAL
) {
    /**
     * Применяет сопротивление к урону.
     * 
     * @param resistance Уровень сопротивления
     * @return Итоговый урон после применения сопротивления
     */
    fun applyResistance(resistance: ResistanceLevel): Float {
        return amount * resistance.multiplier
    }
    
    /**
     * Проверяет, является ли источник урона физическим.
     * 
     * Полезно для проверки условных сопротивлений типа:
     * "Иммунитет к немагическому урону" (source == PHYSICAL)
     */
    fun isPhysical(): Boolean {
        return source == DamageSource.PHYSICAL
    }
    
    /**
     * Проверяет, является ли источник урона магическим.
     */
    fun isMagical(): Boolean {
        return source == DamageSource.MAGICAL
    }
    
    /**
     * Проверяет, является ли источник урона от окружения.
     */
    fun isEnvironmental(): Boolean {
        return source == DamageSource.ENVIRONMENTAL
    }
    
    /**
     * Проверяет, является ли тип урона физическим (slashing/piercing/bludgeoning).
     * 
     * Полезно для проверки условных сопротивлений типа:
     * "Сопротивление к физическому урону" (независимо от источника)
     */
    fun hasPhysicalType(): Boolean {
        return type.path in listOf("slashing", "piercing", "bludgeoning")
    }
    
    /**
     * Создаёт копию с изменённым количеством урона.
     */
    fun withAmount(newAmount: Float): DamageInstance {
        return copy(amount = newAmount)
    }
    
    /**
     * Создаёт копию с изменённым источником.
     */
    fun withSource(newSource: DamageSource): DamageInstance {
        return copy(source = newSource)
    }
    
    override fun toString(): String {
        return "$amount ${type.path} (${source.name.lowercase()})"
    }
}
