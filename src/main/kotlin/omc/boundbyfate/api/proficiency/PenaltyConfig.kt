package omc.boundbyfate.api.proficiency

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import omc.boundbyfate.api.effect.EffectDefinition

/**
 * Конфигурация штрафа за отсутствие владения.
 *
 * Использует систему эффектов — штрафы это просто эффекты которые
 * применяются когда у сущности нет нужного владения.
 *
 * ## Примеры JSON
 *
 * ### Штраф к атаке (оружие без владения)
 * ```json
 * {
 *   "effects": [
 *     {"id": "boundbyfate-core:attack_penalty", "data": {"penalty": -4}}
 *   ]
 * }
 * ```
 *
 * ### Комбинированный штраф (тяжёлая броня без владения)
 * ```json
 * {
 *   "effects": [
 *     {"id": "boundbyfate-core:stealth_disadvantage", "data": {"skills": ["boundbyfate-core:stealth"]}},
 *     {"id": "boundbyfate-core:armor_class_penalty", "data": {"penalty": -2}}
 *   ]
 * }
 * ```
 *
 * ### Запрет использования (инструменты)
 * ```json
 * {
 *   "prohibit": true,
 *   "message": "You don't know how to use this tool"
 * }
 * ```
 */
data class PenaltyConfig(
    /**
     * Список эффектов которые применяются при отсутствии владения.
     *
     * Inline-определения эффектов — id + data.
     * Применяются через EffectApplier, снимаются когда предмет снят.
     */
    val effects: List<EffectDefinition> = emptyList(),

    /**
     * Запретить использование предмета.
     *
     * Если true, предмет нельзя использовать без владения.
     * Используется для инструментов и некоторых видов брони.
     */
    val prohibit: Boolean = false,

    /**
     * Сообщение при запрете использования.
     */
    val message: String? = null
) {
    companion object {
        val CODEC: Codec<PenaltyConfig> = RecordCodecBuilder.create { instance ->
            instance.group(
                EffectDefinition.CODEC.listOf()
                    .optionalFieldOf("effects", emptyList())
                    .forGetter { it.effects },
                Codec.BOOL
                    .optionalFieldOf("prohibit", false)
                    .forGetter { it.prohibit },
                Codec.STRING
                    .optionalFieldOf("message")
                    .forGetter { java.util.Optional.ofNullable(it.message) }
            ).apply(instance) { effects, prohibit, message ->
                PenaltyConfig(
                    effects = effects,
                    prohibit = prohibit,
                    message = message.orElse(null)
                )
            }
        }

        val NONE = PenaltyConfig()
    }

    fun isEmpty(): Boolean = effects.isEmpty() && !prohibit
    fun isNotEmpty(): Boolean = !isEmpty()
}
