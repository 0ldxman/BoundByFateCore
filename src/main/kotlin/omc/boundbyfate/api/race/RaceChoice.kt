package omc.boundbyfate.api.race

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Выбор игрока при создании персонажа расы.
 */
sealed class RaceChoice {

    data class StatBonus(
        val count: Int,
        val from: List<Identifier>,
        val value: Int
    ) : RaceChoice()

    data class Language(
        val count: Int,
        val from: List<Identifier>
    ) : RaceChoice()

    data class Feature(
        val count: Int,
        val from: List<Identifier>
    ) : RaceChoice()

    companion object {

        private val STAT_BONUS_CODEC: Codec<StatBonus> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.INT.fieldOf("count").forGetter { it.count },
                CodecUtil.IDENTIFIER.listOf()
                    .optionalFieldOf("from", emptyList())
                    .forGetter { it.from },
                Codec.INT.fieldOf("value").forGetter { it.value }
            ).apply(i, ::StatBonus)
        }

        private val LANGUAGE_CODEC: Codec<Language> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.INT.fieldOf("count").forGetter { it.count },
                CodecUtil.IDENTIFIER.listOf()
                    .optionalFieldOf("from", emptyList())
                    .forGetter { it.from }
            ).apply(i, ::Language)
        }

        private val FEATURE_CODEC: Codec<Feature> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.INT.fieldOf("count").forGetter { it.count },
                CodecUtil.IDENTIFIER.listOf()
                    .fieldOf("from")
                    .forGetter { it.from }
            ).apply(i, ::Feature)
        }

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
                    "stat_bonus" -> STAT_BONUS_CODEC
                    "language"   -> LANGUAGE_CODEC
                    "feature"    -> FEATURE_CODEC
                    else -> throw IllegalArgumentException("Unknown race choice type: $type")
                }
            }
        )
    }
}
