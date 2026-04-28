package omc.boundbyfate.api.condition

import com.mojang.serialization.Codec
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory

/**
 * Реестр всех зарегистрированных типов условий.
 *
 * Типы регистрируются автоматически через [ConditionType.register].
 *
 * ## CODEC для JSON
 *
 * [CODEC] используется при загрузке JSON-файлов особенностей.
 * Пример JSON:
 * ```json
 * {"type": "boundbyfate-core:has_advantage"}
 * {"type": "boundbyfate-core:hp_below", "percent": 50}
 * ```
 *
 * ## Логические операторы
 *
 * `or`, `and`, `not` — встроены в [Condition] и обрабатываются
 * в [ConditionSystem] рекурсивно. Они не требуют регистрации.
 */
object ConditionTypeRegistry {

    private val logger = LoggerFactory.getLogger(ConditionTypeRegistry::class.java)
    private val types: MutableMap<Identifier, ConditionType<*>> = mutableMapOf()

    /**
     * Регистрирует тип условия.
     * Вызывается автоматически из [ConditionType.register].
     */
    internal fun register(type: ConditionType<*>) {
        if (types.containsKey(type.id)) {
            logger.warn("Overriding condition type '${type.id}'")
        }
        types[type.id] = type
        logger.debug("Registered condition type '${type.id}'")
    }

    /**
     * Возвращает тип условия по ID или null.
     */
    fun get(id: Identifier): ConditionType<*>? = types[id]

    /**
     * Возвращает все зарегистрированные типы.
     */
    fun all(): Collection<ConditionType<*>> = types.values

    /**
     * Количество зарегистрированных типов.
     */
    fun size(): Int = types.size

    /**
     * Codec для сериализации/десериализации [Condition] из JSON.
     * Lazy — вычисляется после регистрации всех типов.
     */
    val CODEC: Codec<Condition<*>> by lazy {
        @Suppress("UNCHECKED_CAST")
        buildCodec() as Codec<Condition<*>>
    }

    private fun buildCodec(): Codec<Condition<Any>> {
        // Логические операторы регистрируем здесь, они встроены
        val logicalCodec = Identifier.CODEC.dispatch(
            "type",
            { condition ->
                when (condition) {
                    is Condition.Or -> Identifier("boundbyfate-core", "or")
                    is Condition.And -> Identifier("boundbyfate-core", "and")
                    is Condition.Not -> Identifier("boundbyfate-core", "not")
                    is Condition.Typed<*> -> condition.type.id
                }
            },
            { id ->
                when (id.toString()) {
                    "boundbyfate-core:or" -> Condition.Or.CODEC
                    "boundbyfate-core:and" -> Condition.And.CODEC
                    "boundbyfate-core:not" -> Condition.Not.CODEC
                    else -> {
                        @Suppress("UNCHECKED_CAST")
                        val condType = types[id] as? ConditionType<Any>
                            ?: throw IllegalArgumentException(
                                "Unknown condition type '$id'. " +
                                "Available types: ${types.keys.joinToString()}"
                            )
                        condType.codec.xmap(
                            { data -> Condition.Typed(condType, data) },
                            { cond ->
                                @Suppress("UNCHECKED_CAST")
                                (cond as Condition.Typed<Any>).data
                            }
                        )
                    }
                }
            }
        )
        return logicalCodec
    }
}

