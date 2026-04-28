package omc.boundbyfate.api.condition

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Контейнер условия.
 *
 * ## Концепция
 *
 * [Condition] — это либо типизированное условие ([Typed]) с данными,
 * либо один из встроенных логических операторов ([Or], [And], [Not]).
 *
 * Логические операторы встроены напрямую, потому что они рекурсивны
 * и не имеют смысла без других условий.
 *
 * ## Использование
 *
 * ```kotlin
 * // Вычислить условие
 * val result = ConditionSystem.evaluate(condition, context)
 *
 * // Вычислить список (все должны выполняться)
 * val allMet = ConditionSystem.evaluateAll(conditions, context)
 * ```
 *
 * ## JSON
 *
 * ```json
 * {"type": "boundbyfate-core:has_advantage"}
 * {"type": "boundbyfate-core:hp_below", "percent": 50}
 * {
 *   "type": "boundbyfate-core:or",
 *   "conditions": [
 *     {"type": "boundbyfate-core:has_advantage"},
 *     {"type": "boundbyfate-core:ally_within", "distance": 5.0}
 *   ]
 * }
 * ```
 */
sealed class Condition<out D> {

    /**
     * Типизированное условие — хранит тип и данные.
     */
    data class Typed<D>(
        val type: ConditionType<D>,
        val data: D
    ) : Condition<D>()

    /**
     * Логическое ИЛИ — хотя бы одно условие должно выполняться.
     *
     * JSON:
     * ```json
     * {
     *   "type": "boundbyfate-core:or",
     *   "conditions": [...]
     * }
     * ```
     */
    data class Or(val conditions: List<Condition<*>>) : Condition<Nothing>() {
        companion object {
            val CODEC: Codec<Or> = RecordCodecBuilder.create { i ->
                i.group(
                    ConditionTypeRegistry.CODEC.listOf()
                        .fieldOf("conditions")
                        .forGetter { it.conditions }
                ).apply(i, ::Or)
            }
        }
    }

    /**
     * Логическое И — все условия должны выполняться.
     *
     * JSON:
     * ```json
     * {
     *   "type": "boundbyfate-core:and",
     *   "conditions": [...]
     * }
     * ```
     */
    data class And(val conditions: List<Condition<*>>) : Condition<Nothing>() {
        companion object {
            val CODEC: Codec<And> = RecordCodecBuilder.create { i ->
                i.group(
                    ConditionTypeRegistry.CODEC.listOf()
                        .fieldOf("conditions")
                        .forGetter { it.conditions }
                ).apply(i, ::And)
            }
        }
    }

    /**
     * Логическое НЕ — инвертирует условие.
     *
     * JSON:
     * ```json
     * {
     *   "type": "boundbyfate-core:not",
     *   "condition": {...}
     * }
     * ```
     */
    data class Not(val condition: Condition<*>) : Condition<Nothing>() {
        companion object {
            val CODEC: Codec<Not> = RecordCodecBuilder.create { i ->
                i.group(
                    ConditionTypeRegistry.CODEC
                        .fieldOf("condition")
                        .forGetter { it.condition }
                ).apply(i, ::Not)
            }
        }
    }

    companion object {
        /**
         * Codec для сериализации/десериализации.
         * Делегирует в [ConditionTypeRegistry.CODEC].
         */
        val CODEC: Codec<Condition<*>>
            get() = ConditionTypeRegistry.CODEC
    }
}
