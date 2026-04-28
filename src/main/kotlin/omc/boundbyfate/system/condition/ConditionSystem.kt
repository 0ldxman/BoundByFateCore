package omc.boundbyfate.system.condition

import omc.boundbyfate.api.condition.Condition
import omc.boundbyfate.api.condition.ConditionContext
import org.slf4j.LoggerFactory

/**
 * Центральная точка вычисления условий.
 *
 * Вычисляет [Condition] используя логику из [omc.boundbyfate.api.condition.ConditionType].
 * Логические операторы (Or, And, Not) обрабатываются встроенно рекурсивно.
 *
 * ## Использование
 *
 * ```kotlin
 * val ctx = ConditionContext(entity = attacker, target = target, advantageType = advantage)
 *
 * // Одно условие
 * val result = ConditionSystem.evaluate(condition, ctx)
 *
 * // Все должны выполняться
 * val allMet = ConditionSystem.evaluateAll(conditions, ctx)
 *
 * // Хотя бы одно
 * val anyMet = ConditionSystem.evaluateAny(conditions, ctx)
 * ```
 */
object ConditionSystem {

    private val logger = LoggerFactory.getLogger(ConditionSystem::class.java)

    /**
     * Вычисляет условие.
     *
     * @param condition условие для проверки
     * @param context контекст проверки
     * @param defaultValue значение если тип не найден (по умолчанию false)
     */
    fun evaluate(
        condition: Condition<*>,
        context: ConditionContext,
        defaultValue: Boolean = false
    ): Boolean = try {
        evaluateInternal(condition, context, defaultValue)
    } catch (e: Exception) {
        logger.error("Error evaluating condition '${condition::class.simpleName}'", e)
        defaultValue
    }

    @Suppress("UNCHECKED_CAST")
    private fun evaluateInternal(
        condition: Condition<*>,
        context: ConditionContext,
        defaultValue: Boolean
    ): Boolean = when (condition) {
        // Логические операторы — встроены, рекурсивны
        is Condition.Or  -> condition.conditions.any { evaluate(it, context, defaultValue) }
        is Condition.And -> condition.conditions.all { evaluate(it, context, defaultValue) }
        is Condition.Not -> !evaluate(condition.condition, context, defaultValue)

        // Типизированное условие — делегируем в ConditionType
        is Condition.Typed<*> -> {
            val typed = condition as Condition.Typed<Any>
            typed.type.evaluate(typed.data, context)
        }
    }

    /**
     * Проверяет, выполняются ли **все** условия.
     * Пустой список → true.
     */
    fun evaluateAll(conditions: List<Condition<*>>, context: ConditionContext): Boolean {
        if (conditions.isEmpty()) return true
        return conditions.all { evaluate(it, context) }
    }

    /**
     * Проверяет, выполняется ли **хотя бы одно** условие.
     * Пустой список → false.
     */
    fun evaluateAny(conditions: List<Condition<*>>, context: ConditionContext): Boolean {
        if (conditions.isEmpty()) return false
        return conditions.any { evaluate(it, context) }
    }
}
