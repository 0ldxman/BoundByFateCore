package omc.boundbyfate.api.race

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Грант расы — одна единица того что раса даёт персонажу.
 */
sealed class RaceGrant {

    data class Size(val size: CreatureSize) : RaceGrant()

    data class Speed(val movement: MovementType, val value: Int) : RaceGrant()

    data class StatBonus(val stat: Identifier, val value: Int) : RaceGrant()

    data class Feature(val id: Identifier) : RaceGrant()

    data class Language(val id: Identifier) : RaceGrant()

    data class Ability(val id: Identifier) : RaceGrant()

    companion object {

        private val SIZE_CODEC: Codec<Size> = RecordCodecBuilder.create { i ->
            i.group(CreatureSize.CODEC.fieldOf("size").forGetter { it.size })
             .apply(i, ::Size)
        }

        private val SPEED_CODEC: Codec<Speed> = RecordCodecBuilder.create { i ->
            i.group(
                MovementType.CODEC.fieldOf("movement").forGetter { it.movement },
                Codec.INT.fieldOf("value").forGetter { it.value }
            ).apply(i, ::Speed)
        }

        private val STAT_BONUS_CODEC: Codec<StatBonus> = RecordCodecBuilder.create { i ->
            i.group(
                CodecUtil.IDENTIFIER.fieldOf("stat").forGetter { it.stat },
                Codec.INT.fieldOf("value").forGetter { it.value }
            ).apply(i, ::StatBonus)
        }

        private val FEATURE_CODEC: Codec<Feature> = RecordCodecBuilder.create { i ->
            i.group(CodecUtil.IDENTIFIER.fieldOf("id").forGetter { it.id })
             .apply(i, ::Feature)
        }

        private val LANGUAGE_CODEC: Codec<Language> = RecordCodecBuilder.create { i ->
            i.group(CodecUtil.IDENTIFIER.fieldOf("id").forGetter { it.id })
             .apply(i, ::Language)
        }

        private val ABILITY_CODEC: Codec<Ability> = RecordCodecBuilder.create { i ->
            i.group(CodecUtil.IDENTIFIER.fieldOf("id").forGetter { it.id })
             .apply(i, ::Ability)
        }

        val CODEC: Codec<RaceGrant> = Identifier.CODEC.dispatch(
            "type",
            { grant ->
                when (grant) {
                    is Size      -> Identifier("boundbyfate-core", "size")
                    is Speed     -> Identifier("boundbyfate-core", "speed")
                    is StatBonus -> Identifier("boundbyfate-core", "stat_bonus")
                    is Feature   -> Identifier("boundbyfate-core", "feature")
                    is Language  -> Identifier("boundbyfate-core", "language")
                    is Ability   -> Identifier("boundbyfate-core", "ability")
                }
            },
            { id ->
                when (id.toString()) {
                    "boundbyfate-core:size"       -> SIZE_CODEC
                    "boundbyfate-core:speed"      -> SPEED_CODEC
                    "boundbyfate-core:stat_bonus" -> STAT_BONUS_CODEC
                    "boundbyfate-core:feature"    -> FEATURE_CODEC
                    "boundbyfate-core:language"   -> LANGUAGE_CODEC
                    "boundbyfate-core:ability"    -> ABILITY_CODEC
                    else -> throw IllegalArgumentException("Unknown race grant type: $id")
                }
            }
        )
    }
}
