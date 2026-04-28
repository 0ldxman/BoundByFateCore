package omc.boundbyfate.api.effect

import net.minecraft.entity.LivingEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.util.source.SourceReference

/**
 * Контекст применения эффекта.
 *
 * Передаётся в [EffectHandler] при apply/remove/tick.
 * Содержит всю информацию которая может понадобиться хендлеру.
 *
 * ## Использование
 *
 * ```kotlin
 * object Darkvision : EffectHandler() {
 *     override fun apply(ctx: EffectContext) {
 *         val range = ctx.data.getInt("range", 60)
 *         // ctx.entity — сущность к которой применяется
 *         // ctx.source — откуда пришёл эффект (раса, класс, предмет...)
 *     }
 * }
 * ```
 *
 * ## Передача данных между хуками
 *
 * Используй [stash] для передачи данных между apply/tick/remove:
 *
 * ```kotlin
 * override fun apply(ctx: EffectContext) {
 *     ctx.stash["initial_hp"] = ctx.entity.health
 * }
 *
 * override fun tick(ctx: EffectContext) {
 *     val initialHp = ctx.stash["initial_hp"] as? Float ?: return
 * }
 * ```
 *
 * @property entity сущность к которой применяется эффект
 * @property definition определение эффекта с параметрами
 * @property source источник эффекта (раса, класс, предмет, заклинание...)
 * @property trigger триггер применения
 * @property target цель (если эффект направлен на другую сущность)
 * @property weapon оружие (если применимо)
 * @property ticksActive сколько тиков эффект уже активен (для tick хука)
 * @property stash хранилище данных между хуками
 */
data class EffectContext(
    val entity: LivingEntity,
    val definition: EffectDefinition,
    val source: SourceReference,
    val trigger: String = TRIGGER_PASSIVE,
    val target: LivingEntity? = null,
    val weapon: ItemStack? = null,
    val ticksActive: Int = 0,
    val stash: MutableMap<String, Any> = mutableMapOf()
) {
    /**
     * Удобный доступ к параметрам эффекта.
     */
    val data: EffectData get() = definition.effectData

    /**
     * Возвращает значение из stash.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getStash(key: String): T? = stash[key] as? T

    /**
     * Кладёт значение в stash.
     */
    fun <T : Any> putStash(key: String, value: T) { stash[key] = value }

    /**
     * Проверяет является ли сущность серверным игроком.
     */
    fun isServerPlayer(): Boolean = entity is ServerPlayerEntity

    /**
     * Возвращает сущность как серверного игрока или null.
     */
    fun asServerPlayer(): ServerPlayerEntity? = entity as? ServerPlayerEntity

    companion object {
        /** Пассивный эффект — применяется при загрузке персонажа. */
        const val TRIGGER_PASSIVE = "passive"

        /** Эффект при надевании предмета. */
        const val TRIGGER_ON_EQUIP = "on_equip"

        /** Эффект при снятии предмета. */
        const val TRIGGER_ON_UNEQUIP = "on_unequip"

        /** Эффект при повышении уровня. */
        const val TRIGGER_LEVEL_UP = "level_up"

        /** Эффект при начале хода. */
        const val TRIGGER_TURN_START = "turn_start"

        /** Эффект при конце хода. */
        const val TRIGGER_TURN_END = "turn_end"

        /** Эффект при попадании атакой. */
        const val TRIGGER_ATTACK_HIT = "attack_hit"

        /** Эффект при получении урона. */
        const val TRIGGER_TAKE_DAMAGE = "take_damage"

        /** Эффект при нанесении урона. */
        const val TRIGGER_DEAL_DAMAGE = "deal_damage"

        /** Эффект при активации способности. */
        const val TRIGGER_ABILITY_ACTIVATE = "ability_activate"

        /** Эффект при отдыхе. */
        const val TRIGGER_REST = "rest"

        /**
         * Создаёт контекст для пассивного эффекта.
         */
        fun passive(
            entity: LivingEntity,
            definition: EffectDefinition,
            source: SourceReference
        ): EffectContext = EffectContext(
            entity = entity,
            definition = definition,
            source = source,
            trigger = TRIGGER_PASSIVE
        )

        /**
         * Создаёт контекст для эффекта при атаке.
         */
        fun attack(
            entity: LivingEntity,
            definition: EffectDefinition,
            source: SourceReference,
            target: LivingEntity,
            weapon: ItemStack? = null
        ): EffectContext = EffectContext(
            entity = entity,
            definition = definition,
            source = source,
            trigger = TRIGGER_ATTACK_HIT,
            target = target,
            weapon = weapon
        )

        /**
         * Создаёт контекст для тика длящегося эффекта.
         */
        fun tick(
            entity: LivingEntity,
            definition: EffectDefinition,
            source: SourceReference,
            ticksActive: Int,
            stash: MutableMap<String, Any>
        ): EffectContext = EffectContext(
            entity = entity,
            definition = definition,
            source = source,
            trigger = TRIGGER_PASSIVE,
            ticksActive = ticksActive,
            stash = stash
        )
    }
}
