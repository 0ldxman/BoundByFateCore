package omc.boundbyfate.api.item

import net.minecraft.util.Identifier

/**
 * Базовый класс логики свойства предмета.
 *
 * Аналог [omc.boundbyfate.api.effect.EffectHandler] и
 * [omc.boundbyfate.api.ability.AbilityHandler].
 *
 * Каждое свойство — это `object` наследующий [ItemPropertyHandler].
 * Обязателен только [id]. Все хуки опциональны.
 *
 * ## Создание свойства
 *
 * ```kotlin
 * object StatBonus : ItemPropertyHandler() {
 *     override val id = Identifier.of("boundbyfate-core", "stat_bonus")
 *
 *     override fun onEquip(ctx: ItemPropertyContext) {
 *         val stat  = ctx.data.requireId("stat")
 *         val value = ctx.data.requireInt("value")
 *         ctx.addStatModifier(stat, value)
 *     }
 *
 *     override fun onUnequip(ctx: ItemPropertyContext) {
 *         val stat = ctx.data.requireId("stat")
 *         ctx.removeStatModifier(stat)
 *     }
 * }
 * ```
 *
 * ## Тикующее свойство
 *
 * ```kotlin
 * object PoisonedBlade : ItemPropertyHandler() {
 *     override val id = Identifier.of("boundbyfate-core", "poisoned_blade")
 *     override val ticksInMainHand = true
 *     override val tickInterval = 20
 *
 *     override fun onHeldTick(ctx: ItemPropertyContext) {
 *         // Каждую секунду пока меч в руке
 *         spawnPoisonParticles(ctx.entity)
 *     }
 *
 *     override fun onHit(ctx: ItemPropertyContext) {
 *         // При попадании — наложить яд
 *         ctx.applyStatusToTarget(POISONED_ID, Duration.seconds(10))
 *     }
 * }
 * ```
 *
 * ## Хуки
 *
 * ```
 * onEquip()       — надели в любой слот
 * onUnequip()     — сняли из любого слота
 * onHeldTick()    — каждые tickInterval тиков пока в основной руке
 * onOffhandTick() — каждые tickInterval тиков пока в левой руке
 * onWornTick()    — каждые tickInterval тиков пока надето (броня/украшения)
 * onAttack()      — атакуешь этим предметом
 * onHit()         — попал по цели этим предметом
 * ```
 *
 * ## Регистрация
 *
 * ```kotlin
 * // В BbfItemProperties.register():
 * ItemPropertyRegistry.register(StatBonus)
 * ItemPropertyRegistry.register(MeleeDamage)
 * ```
 */
abstract class ItemPropertyHandler {

    /**
     * Уникальный идентификатор свойства.
     * Должен совпадать с `id` в JSON.
     */
    abstract val id: Identifier

    // ── Тикование — opt-in ────────────────────────────────────────────────

    /**
     * Тикует пока предмет в основной руке.
     * По умолчанию false — не тикует.
     */
    open val ticksInMainHand: Boolean = false

    /**
     * Тикует пока предмет в левой руке.
     * По умолчанию false — не тикует.
     */
    open val ticksInOffhand: Boolean = false

    /**
     * Тикует пока предмет надето (броня, украшения).
     * По умолчанию false — не тикует.
     */
    open val ticksWhenWorn: Boolean = false

    /**
     * Интервал тикования в тиках.
     * 20 = раз в секунду.
     */
    open val tickInterval: Int = 20

    /**
     * Является ли свойство тикующим хотя бы в одном контексте.
     */
    val isTicking: Boolean
        get() = ticksInMainHand || ticksInOffhand || ticksWhenWorn

    // ── Хуки экипировки ───────────────────────────────────────────────────

    /**
     * Вызывается когда предмет надевается в любой слот.
     *
     * Используй для применения постоянных эффектов, выдачи способностей,
     * добавления ресурсов.
     */
    open fun onEquip(ctx: ItemPropertyContext) {}

    /**
     * Вызывается когда предмет снимается из любого слота.
     *
     * Используй для снятия всего что дал [onEquip].
     * Снимай по [ItemPropertyContext.source] — это гарантирует
     * что снимается именно то что дал этот предмет в этом слоте.
     */
    open fun onUnequip(ctx: ItemPropertyContext) {}

    // ── Хуки тикования ────────────────────────────────────────────────────

    /**
     * Вызывается каждые [tickInterval] тиков пока предмет в основной руке.
     * Только если [ticksInMainHand] = true.
     */
    open fun onHeldTick(ctx: ItemPropertyContext) {}

    /**
     * Вызывается каждые [tickInterval] тиков пока предмет в левой руке.
     * Только если [ticksInOffhand] = true.
     */
    open fun onOffhandTick(ctx: ItemPropertyContext) {}

    /**
     * Вызывается каждые [tickInterval] тиков пока предмет надето.
     * Только если [ticksWhenWorn] = true.
     */
    open fun onWornTick(ctx: ItemPropertyContext) {}

    // ── Хуки боя ──────────────────────────────────────────────────────────

    /**
     * Вызывается при атаке этим предметом (до броска).
     *
     * Используй для модификации броска атаки, добавления бонусов.
     */
    open fun onAttack(ctx: ItemPropertyContext) {}

    /**
     * Вызывается при попадании по цели этим предметом.
     *
     * Используй для эффектов при попадании (яд, огонь, нокдаун).
     */
    open fun onHit(ctx: ItemPropertyContext) {}

    // ── Хуки взаимодействия ───────────────────────────────────────────────

    /**
     * Вызывается при правом клике на блок с предметом в руке.
     *
     * Используй для предметов которые взаимодействуют с блоками:
     * - Воровские инструменты + замок → мини-игра вскрытия
     * - Ловушки + блок → установка ловушки
     * - Зелье + блок → вылить на блок
     *
     * [ItemPropertyContext.blockHit] содержит информацию о блоке.
     *
     * @return true если взаимодействие было обработано (отменяет ванильное поведение)
     */
    open fun onUseOnBlock(ctx: ItemPropertyContext): Boolean = false

    /**
     * Вызывается при правом клике на сущность с предметом в руке.
     *
     * Используй для предметов которые взаимодействуют с существами:
     * - Верёвка + существо → связать
     * - Зелье + существо → применить эффект
     * - Инструмент + НПС → особое взаимодействие
     *
     * [ItemPropertyContext.entityHit] содержит информацию о сущности.
     *
     * @return true если взаимодействие было обработано (отменяет ванильное поведение)
     */
    open fun onUseOnEntity(ctx: ItemPropertyContext): Boolean = false

    /**
     * Вызывается при правом клике в воздух с предметом в руке.
     *
     * Используй для предметов с активным использованием без цели:
     * - Свиток → прочитать заклинание
     * - Зелье → выпить
     * - Музыкальный инструмент → сыграть
     *
     * @return true если взаимодействие было обработано (отменяет ванильное поведение)
     */
    open fun onUse(ctx: ItemPropertyContext): Boolean = false

    override fun toString(): String = "ItemPropertyHandler($id)"
}
