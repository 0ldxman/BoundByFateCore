package omc.boundbyfate.data.world.character

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier

/**
 * Механические характеристики персонажа — "что ты умеешь".
 *
 * Хранит только базовые значения. Вычисленные значения (с бонусами от расы,
 * класса, фитов) живут в компонентах (Attachments) и пересоздаются при загрузке.
 */
data class CharacterStats(
    /** Базовые значения характеристик. Ключ — ID стата (например "boundbyfate-core:strength"). */
    val baseStats: Map<String, Int> = emptyMap(),
    /** Список ID владений персонажа. */
    val proficiencies: List<Identifier> = emptyList(),
    /** Жизненная сила и шрамы. */
    val vitality: CharacterVitality = CharacterVitality(),
    /** Данные способностей. */
    val abilities: CharacterAbilities = CharacterAbilities()
) {
    companion object {
        val CODEC: Codec<CharacterStats> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .fieldOf("baseStats").forGetter { it.baseStats },
                Identifier.CODEC.listOf()
                    .fieldOf("proficiencies").forGetter { it.proficiencies },
                CharacterVitality.CODEC.fieldOf("vitality").forGetter { it.vitality },
                CharacterAbilities.CODEC.fieldOf("abilities").forGetter { it.abilities }
            ).apply(instance, ::CharacterStats)
        }
    }
}

/**
 * Жизненная сила и физические последствия.
 *
 * @property vitalityScale шкала жизненной силы (0–100)
 * @property scars список шрамов персонажа
 */
data class CharacterVitality(
    val vitalityScale: Int = 100,
    val scars: List<Scar> = emptyList()
) {
    companion object {
        val CODEC: Codec<CharacterVitality> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("vitalityScale").forGetter { it.vitalityScale },
                Scar.CODEC.listOf().fieldOf("scars").forGetter { it.scars }
            ).apply(instance, ::CharacterVitality)
        }
    }
}

/**
 * Шрам персонажа — физическое или психологическое последствие.
 *
 * @property id ID шрама (из датапака или сгенерированный)
 * @property description описание шрама
 * @property acquiredAt игровое время когда получен
 */
data class Scar(
    val id: String,
    val description: String,
    val acquiredAt: Long = 0L
) {
    companion object {
        val CODEC: Codec<Scar> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter { it.id },
                Codec.STRING.fieldOf("description").forGetter { it.description },
                Codec.LONG.fieldOf("acquiredAt").forGetter { it.acquiredAt }
            ).apply(instance, ::Scar)
        }
    }
}

/**
 * Данные способностей персонажа.
 *
 * @property knownAbilities список ID известных способностей
 * @property abilityHotbar расположение способностей на хотбаре (слот → ID способности)
 */
data class CharacterAbilities(
    val knownAbilities: List<Identifier> = emptyList(),
    val abilityHotbar: Map<Int, Identifier> = emptyMap()
) {
    companion object {
        val CODEC: Codec<CharacterAbilities> = RecordCodecBuilder.create { instance ->
            instance.group(
                Identifier.CODEC.listOf()
                    .fieldOf("knownAbilities").forGetter { it.knownAbilities },
                Codec.unboundedMap(
                    Codec.STRING.xmap({ it.toInt() }, { it.toString() }),
                    Identifier.CODEC
                ).fieldOf("abilityHotbar").forGetter { it.abilityHotbar }
            ).apply(instance, ::CharacterAbilities)
        }
    }
}
