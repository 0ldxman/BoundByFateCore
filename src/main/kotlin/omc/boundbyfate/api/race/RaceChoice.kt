package omc.boundbyfate.api.race

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Выбор игрока при создании персонажа расы.
 *
 * Отделён от [RaceGrant] — гранты фиксированы, выборы требуют
 * взаимодействия с игроком при создании персонажа.
 *
 * ## JSON
 *
 * ```json
 * // Выбрать 2 любых стата +1 (Tasha's variant)
 * { "type": "stat_bonus", "count": 2, "from": [], "value": 1 }
 *
 * // Выбрать 1 из конкретных статов +2
 * { "type": "stat_bonus", "count": 1, "from": ["boundbyfate-core:strength", "boundbyfate-core:dexterity"], "value": 2 }
 *
 * // Выбрать 1 любой язык
 * { "type": "language", "count": 1, "from": [] }
 *
 * // Выбрать 1 особенность из списка
 * { "type": "feature", "count": 1, "from": ["boundbyfate-core:feat_a", "boundbyfate-core:feat_b"] }
 * ```
 */
sealed class RaceChoice {

    /**
     * Выбор бонуса к характеристике.
     *
     * @property count сколько характеристик выбрать
     * @property from из каких характеристик выбирать (пустой = любая)
     * @property value величина бонуса для каждой выбранной характеристики
     */
    data class StatBonus(
        val count: Int,
        val from: List<Identifier>,
        val value: Int
    ) : RaceChoice()

    /**
     * Выбор языка.
     *
     * @property count сколько языков выбрать
     * @property from из каких языков выбирать (пустой = любой)
     */
    data class Language(
        val count: Int,
        val from: List<Identifier>
    ) : RaceChoice()

    /**
     * Выбор особенности из списка.
     *
     * @property count сколько особенностей выбрать
     * @property from из каких особенностей выбирать
     */
    data class Feature(
        val count: Int,
        val from: List<Identifier>
    ) : RaceChoice()

    companion object {
        val CODEC: Codec<RaceChoice> = Codec.STRING.dispatch(
            "type",
            { choice ->
                when (choice) {
                    is StatBonus -> "stat_bonus"
                    is Language  -> "language"
                    is Feature   -> "feature"
                }
            },
            { type ->
                when (type) {
                    "stat_bonus" -> StatBonus.CODEC
                    "language"   -> Language.CODEC
                    "feature"    -> Feature.CODEC
                    else -> throw IllegalArgumentException("Unknown race choice type: $type")
                }
            }
        )

        private val StatBonus.Companion.CODEC: Codec<StatBonus>
            get() = RecordCodecBuilder.create { i ->
                i.group(
                    Codec.INT.fieldOf("count").forGetter { it.count },
                    CodecUtil.IDENTIFIER.listOf()
                        .optionalFieldOf("from", emptyList())
                        .forGetter { it.from },
                    Codec.INT.fieldOf("value").forGetter { it.value }
                ).apply(i, ::StatBonus)
            }

        private val Language.Companion.CODEC: Codec<Language>
            get() = RecordCodecBuilder.create { i ->
                i.group(
                    Codec.INT.fieldOf("count").forGetter { it.count },
                    CodecUtil.IDENTIFIER.listOf()
                        .optionalFieldOf("from", emptyList())
                        .forGetter { it.from }
                ).apply(i, ::Language)
            }

        private val Feature.Companion.CODEC: Codec<Feature>
            get() = RecordCodecBuilder.create { i ->
                i.group(
                    Codec.INT.fieldOf("count").forGetter { it.count },
                    CodecUtil.IDENTIFIER.listOf()
                        .fieldOf("from")
                        .forGetter { it.from }
                ).apply(i, ::Feature)
            }
    }
}
