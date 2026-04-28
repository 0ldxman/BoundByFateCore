package omc.boundbyfate.api.damage

import com.mojang.serialization.Codec

/**
 * Источник урона — описывает КАК был нанесён урон.
 * 
 * В D&D 5e и модах важно различать природу источника урона:
 * - **PHYSICAL** — физический урон от обычного оружия и атак
 * - **MAGICAL** — магический урон от заклинаний и магического оружия
 * - **ENVIRONMENTAL** — урон от окружения (ловушки, падение, лава)
 * 
 * Это влияет на взаимодействие с существами и эффектами:
 * - Некоторые существа имеют "сопротивление к немагическому урону"
 * - Некоторые существа имеют "иммунитет к урону от окружения"
 * - Магическое оружие может преодолевать защиты
 * 
 * ## Примеры
 * 
 * ### Физическое оружие
 * ```kotlin
 * // Обычный меч
 * DamageInstance(
 *     type = Identifier("dnd", "slashing"),
 *     amount = 8f,
 *     source = DamageSource.PHYSICAL
 * )
 * 
 * // Обычный лук
 * DamageInstance(
 *     type = Identifier("dnd", "piercing"),
 *     amount = 6f,
 *     source = DamageSource.PHYSICAL
 * )
 * ```
 * 
 * ### Магическое оружие и заклинания
 * ```kotlin
 * // Магический меч +1
 * DamageInstance(
 *     type = Identifier("dnd", "slashing"),
 *     amount = 9f,
 *     source = DamageSource.MAGICAL
 * )
 * 
 * // Flame Tongue (магический меч с огнём)
 * listOf(
 *     DamageInstance(Identifier("dnd", "slashing"), 8f, DamageSource.MAGICAL),
 *     DamageInstance(Identifier("dnd", "fire"), 7f, DamageSource.MAGICAL)
 * )
 * 
 * // Fireball
 * DamageInstance(
 *     type = Identifier("dnd", "fire"),
 *     amount = 28f,
 *     source = DamageSource.MAGICAL
 * )
 * ```
 * 
 * ### Окружение
 * ```kotlin
 * // Падение
 * DamageInstance(
 *     type = Identifier("dnd", "bludgeoning"),
 *     amount = 20f,
 *     source = DamageSource.ENVIRONMENTAL
 * )
 * 
 * // Лава
 * DamageInstance(
 *     type = Identifier("dnd", "fire"),
 *     amount = 10f,
 *     source = DamageSource.ENVIRONMENTAL
 * )
 * 
 * // Горящая бочка (взорвалась)
 * DamageInstance(
 *     type = Identifier("dnd", "fire"),
 *     amount = 7f,
 *     source = DamageSource.ENVIRONMENTAL
 * )
 * 
 * // Магическая ловушка (магический огонь)
 * DamageInstance(
 *     type = Identifier("dnd", "fire"),
 *     amount = 14f,
 *     source = DamageSource.MAGICAL
 * )
 * ```
 * 
 * ### Кинжал с горючей смесью
 * ```kotlin
 * // 1d4 колющего (физический) + 1d4 огненного (окружение)
 * listOf(
 *     DamageInstance(Identifier("dnd", "piercing"), 4f, DamageSource.PHYSICAL),
 *     DamageInstance(Identifier("dnd", "fire"), 4f, DamageSource.ENVIRONMENTAL)
 * )
 * ```
 * 
 * ### Условные сопротивления
 * ```kotlin
 * // Оборотень: иммунитет к немагическому физическому урону
 * val werewolfCondition: (DamageInstance) -> Boolean = { damage ->
 *     damage.source == DamageSource.PHYSICAL && damage.isPhysical()
 * }
 * 
 * // Обычный меч: 10 slashing (PHYSICAL) → 0 урона (иммунитет)
 * // Магический меч: 10 slashing (MAGICAL) → 10 урона (нет иммунитета)
 * 
 * // Голем: иммунитет к урону от окружения
 * val golemCondition: (DamageInstance) -> Boolean = { damage ->
 *     damage.source == DamageSource.ENVIRONMENTAL
 * }
 * 
 * // Падение: 20 bludgeoning (ENVIRONMENTAL) → 0 урона (иммунитет)
 * // Молот: 15 bludgeoning (PHYSICAL) → 15 урона (нет иммунитета)
 * ```
 * 
 * ## Правила D&D 5e
 * 
 * Из официальных правил:
 * - Магическое оружие (+1, +2, +3) наносит магический урон
 * - Серебряное оружие считается магическим против оборотней
 * - Заклинания всегда наносят магический урон
 * - Обычное оружие наносит физический урон
 * - Урон от окружения (падение, лава, яд) — environmental
 * - Магические ловушки наносят магический урон
 */
enum class DamageSource {
    /**
     * Физический урон от оружия и атак.
     * 
     * Источники:
     * - Обычное оружие (меч, лук, кинжал)
     * - Атаки обычных существ (когти, укусы)
     * - Рукопашные атаки
     * 
     * Примеры:
     * - Обычный меч: slashing (PHYSICAL)
     * - Обычный лук: piercing (PHYSICAL)
     * - Кулак: bludgeoning (PHYSICAL)
     */
    PHYSICAL,
    
    /**
     * Магический урон от заклинаний и магических эффектов.
     * 
     * Источники:
     * - Магическое оружие (+1, +2, +3)
     * - Заклинания (Fireball, Magic Missile)
     * - Магические эффекты
     * - Атаки магических существ
     * - Магические ловушки
     * 
     * Примеры:
     * - Магический меч +1: slashing (MAGICAL)
     * - Fireball: fire (MAGICAL)
     * - Flame Tongue: slashing (MAGICAL) + fire (MAGICAL)
     */
    MAGICAL,
    
    /**
     * Урон от окружения и природных опасностей.
     * 
     * Источники:
     * - Падение
     * - Лава, огонь
     * - Яд (природный)
     * - Удушье, утопление
     * - Обычные ловушки
     * - Взрывы (немагические)
     * 
     * Примеры:
     * - Падение: bludgeoning (ENVIRONMENTAL)
     * - Лава: fire (ENVIRONMENTAL)
     * - Горящая бочка: fire (ENVIRONMENTAL)
     * - Кинжал с ядом: piercing (PHYSICAL) + poison (ENVIRONMENTAL)
     */
    ENVIRONMENTAL;
    
    companion object {
        /**
         * Codec для сериализации/десериализации.
         */
        val CODEC: Codec<DamageSource> = Codec.STRING.xmap(
            { str -> valueOf(str.uppercase()) },
            { source -> source.name.lowercase() }
        )
    }
}
