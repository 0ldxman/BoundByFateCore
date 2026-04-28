package omc.boundbyfate.api.condition

import net.minecraft.entity.LivingEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier
import omc.boundbyfate.api.dice.AdvantageType

/**
 * Контекст вычисления условия.
 *
 * Передаётся в [ConditionEvaluator] при проверке условия.
 * Содержит всю информацию о текущей ситуации.
 *
 * Философия:
 * - Контекст — это "снимок" ситуации в момент проверки
 * - Evaluator не хранит состояние — всё через контекст
 * - Расширяем через [extras] для кастомных условий
 *
 * @property entity сущность, для которой проверяется условие
 * @property target цель (если условие проверяется в контексте атаки/заклинания)
 * @property weapon оружие в руке (если применимо)
 * @property advantageType текущий тип преимущества/помехи
 * @property trigger триггер, в котором проверяется условие
 * @property extras дополнительные данные для кастомных условий
 */
data class ConditionContext(
    val entity: LivingEntity,
    val target: LivingEntity? = null,
    val weapon: ItemStack? = null,
    val advantageType: AdvantageType = AdvantageType.NONE,
    val trigger: String = "",
    val extras: Map<String, Any> = emptyMap()
) {
    /**
     * Возвращает дополнительное значение по ключу.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getExtra(key: String): T? = extras[key] as? T

    companion object {
        /**
         * Создаёт контекст для проверки в боевой ситуации.
         */
        fun combat(
            attacker: LivingEntity,
            target: LivingEntity,
            weapon: ItemStack?,
            advantageType: AdvantageType = AdvantageType.NONE,
            trigger: String = ""
        ): ConditionContext = ConditionContext(
            entity = attacker,
            target = target,
            weapon = weapon,
            advantageType = advantageType,
            trigger = trigger
        )

        /**
         * Создаёт минимальный контекст только с сущностью.
         */
        fun of(entity: LivingEntity): ConditionContext = ConditionContext(entity = entity)
    }
}
