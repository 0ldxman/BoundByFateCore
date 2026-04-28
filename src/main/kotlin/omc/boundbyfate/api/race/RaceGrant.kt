package omc.boundbyfate.api.race

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Грант расы — одна единица того что раса даёт персонажу.
 *
 * Раса — это список грантов. Такой подход позволяет:
 * - Легко мёрджить расу и подрасу
 * - Добавлять новые типы грантов без изменения [RaceDefinition]
 * - Единообразно итерировать по всему что даёт раса
 *
 * ## Мёрдж подрасы
 *
 * При мёрдже подраса **переопределяет** гранты родителя того же типа
 * (например, подраса с `Speed(WALK, 30)` заменяет `Speed(WALK, 25)` родителя).
 * Гранты которых нет в подрасе — берутся из родителя.
 *
 * ## JSON
 *
 * ```json
 * { "type": "size", "size": "medium" }
 * { "type": "speed", "movement": "walk", "value": 30 }
 * { "type": "stat_bonus", "stat": "boundbyfate-core:constitution", "value": 2 }
 * { "type": "feature", "id": "boundbyfate-core:darkvision_60" }
 * { "type": "language", "id": "boundbyfate-core:lang_common" }
 * { "type": "ability", "id": "boundbyfate-core:breath_weapon" }
 * ```
 */
sealed class RaceGrant {

    /**
     * Игровой размер существа.
     * Определяет хитбокс и механические правила.
     */
    data class Size(val size: CreatureSize) : RaceGrant()

    /**
     * Скорость передвижения в футах D&D.
     *
     * @property movement тип передвижения
     * @property value скорость в футах
     */
    data class Speed(val movement: MovementType, val value: Int) : RaceGrant()

    /**
     * Фиксированный бонус к характеристике.
     *
     * @property stat ID характеристики
     * @property value величина бонуса (обычно +1 или +2)
     */
    data class StatBonus(val stat: Identifier, val value: Int) : RaceGrant()

    /**
     * Особенность (пассивная способность).
     * Ссылается на [omc.boundbyfate.api.feature.FeatureDefinition].
     *
     * @property id ID особенности
     */
    data class Feature(val id: Identifier) : RaceGrant()

    /**
     * Язык (через систему владений).
     * Ссылается на [omc.boundbyfate.api.proficiency.ProficiencyDefinition] с тегом "language".
     *
     * @property id ID владения-языка
     */
    data class Language(val id: Identifier) : RaceGrant()

    /**
     * Активная способность.
     * Ссылается на [omc.boundbyfate.api.ability.AbilityDefinition].
     *
     * @property id ID способности
     */
    data class Ability(val id: Identifier) : RaceGrant()

    companion object {
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
                    "boundbyfate-core:size"      -> Size.CODEC
                    "boundbyfate-core:speed"     -> Speed.CODEC
                    "boundbyfate-core:stat_bonus" -> StatBonus.CODEC
                    "boundbyfate-core:feature"   -> Feature.CODEC
                    "boundbyfate-core:language"  -> Language.CODEC
                    "boundbyfate-core:ability"   -> Ability.CODEC
                    else -> throw IllegalArgumentException("Unknown race grant type: $id")
                }
            }
        )

        // ── Codec'и для каждого типа ──────────────────────────────────────

        private val Size.Companion.CODEC: Codec<Size>
            get() = RecordCodecBuilder.create { i ->
                i.group(CreatureSize.CODEC.fieldOf("size").forGetter { it.size })
                 .apply(i, ::Size)
            }

        private val Speed.Companion.CODEC: Codec<Speed>
            get() = RecordCodecBuilder.create { i ->
                i.group(
                    MovementType.CODEC.fieldOf("movement").forGetter { it.movement },
                    Codec.INT.fieldOf("value").forGetter { it.value }
                ).apply(i, ::Speed)
            }

        private val StatBonus.Companion.CODEC: Codec<StatBonus>
            get() = RecordCodecBuilder.create { i ->
                i.group(
                    CodecUtil.IDENTIFIER.fieldOf("stat").forGetter { it.stat },
                    Codec.INT.fieldOf("value").forGetter { it.value }
                ).apply(i, ::StatBonus)
            }

        private val Feature.Companion.CODEC: Codec<Feature>
            get() = RecordCodecBuilder.create { i ->
                i.group(CodecUtil.IDENTIFIER.fieldOf("id").forGetter { it.id })
                 .apply(i, ::Feature)
            }

        private val Language.Companion.CODEC: Codec<Language>
            get() = RecordCodecBuilder.create { i ->
                i.group(CodecUtil.IDENTIFIER.fieldOf("id").forGetter { it.id })
                 .apply(i, ::Language)
            }

        private val Ability.Companion.CODEC: Codec<Ability>
            get() = RecordCodecBuilder.create { i ->
                i.group(CodecUtil.IDENTIFIER.fieldOf("id").forGetter { it.id })
                 .apply(i, ::Ability)
            }
    }
}
