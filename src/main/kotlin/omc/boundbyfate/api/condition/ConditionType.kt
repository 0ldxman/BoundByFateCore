package omc.boundbyfate.api.condition

import com.mojang.serialization.Codec
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory

/**
 * Тип условия — объединяет данные (Codec) и логику (evaluate) в одном месте.
 *
 * ## Концепция
 *
 * Аналог [omc.boundbyfate.api.effect.EffectType] для условий.
 * Каждый тип условия — один объект, который:
 * - знает как десериализовать параметры из JSON
 * - знает как вычислить условие
 * - регистрирует себя автоматически
 *
 * ## Создание нового типа условия
 *
 * Без параметров:
 * ```kotlin
 * val HAS_ADVANTAGE = ConditionType.register(
 *     id = "boundbyfate-core:has_advantage",
 *     codec = Codec.unit(Unit),
 *     evaluate = { _, ctx -> ctx.advantageType == AdvantageType.ADVANTAGE }
 * )
 * ```
 *
 * С параметрами:
 * ```kotlin
 * data class HpBelowData(val percent: Int)
 *
 * val HP_BELOW = ConditionType.register(
 *     id = "boundbyfate-core:hp_below",
 *     codec = RecordCodecBuilder.create { i ->
 *         i.group(Codec.INT.fieldOf("percent").forGetter { it.percent })
 *          .apply(i, ::HpBelowData)
 *     },
 *     evaluate = { data, ctx ->
 *         ctx.entity.health / ctx.entity.maxHealth * 100 < data.percent
 *     }
 * )
 * ```
 *
 * Сложная логика — вынести в функцию:
 * ```kotlin
 * val FLANKING = ConditionType.register(
 *     id = "boundbyfate-core:flanking",
 *     codec = Codec.unit(Unit),
 *     evaluate = ::evaluateFlanking
 * )
 * ```
 *
 * @param D тип данных параметров условия
 */
class ConditionType<D> private constructor(
    val id: Identifier,
    val codec: Codec<D>,
    private val evaluateFn: (D, ConditionContext) -> Boolean
) {
    /**
     * Вычисляет условие.
     *
     * @return true если условие выполнено
     */
    fun evaluate(data: D, context: ConditionContext): Boolean = evaluateFn(data, context)

    override fun toString(): String = "ConditionType($id)"

    companion object {
        private val logger = LoggerFactory.getLogger(ConditionType::class.java)

        /**
         * Создаёт и регистрирует новый тип условия.
         *
         * Регистрация происходит автоматически.
         *
         * @param id уникальный идентификатор (например, "boundbyfate-core:has_advantage")
         * @param codec codec для десериализации параметров из JSON
         * @param evaluate логика вычисления условия
         */
        fun <D> register(
            id: String,
            codec: Codec<D>,
            evaluate: (D, ConditionContext) -> Boolean
        ): ConditionType<D> {
            val identifier = Identifier.of(
                id.substringBefore(':'),
                id.substringAfter(':')
            )
            val type = ConditionType(identifier, codec, evaluate)
            ConditionTypeRegistry.register(type)
            return type
        }
    }
}
