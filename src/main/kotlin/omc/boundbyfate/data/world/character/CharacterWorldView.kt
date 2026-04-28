package omc.boundbyfate.data.world.character

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.api.alignment.AlignmentPoint

/**
 * Мировоззрение и психологический профиль персонажа.
 */
data class CharacterWorldView(
    val alignment: CharacterAlignment = CharacterAlignment(),
    val beliefs: List<Belief> = emptyList(),
    val weaknesses: List<Weakness> = emptyList(),
    val motivations: List<Motivation> = emptyList(),
    val stressScale: Int = 0
) {
    companion object {
        val CODEC: Codec<CharacterWorldView> = RecordCodecBuilder.create { instance ->
            instance.group(
                CharacterAlignment.CODEC.fieldOf("alignment").forGetter { it.alignment },
                Belief.CODEC.listOf().fieldOf("beliefs").forGetter { it.beliefs },
                Weakness.CODEC.listOf().fieldOf("weaknesses").forGetter { it.weaknesses },
                Motivation.CODEC.listOf().fieldOf("motivations").forGetter { it.motivations },
                Codec.INT.fieldOf("stressScale").forGetter { it.stressScale }
            ).apply(instance, ::CharacterWorldView)
        }
    }
}

/**
 * Данные мировоззрения персонажа.
 *
 * @property alignmentPoint текущие координаты на сетке мировоззрения
 * @property previousAlignment предыдущее мировоззрение (для отыгрыша изменений)
 */
data class CharacterAlignment(
    val alignmentPoint: AlignmentPoint = AlignmentPoint.NEUTRAL,
    val previousAlignment: AlignmentPoint? = null
) {
    companion object {
        val CODEC: Codec<CharacterAlignment> = RecordCodecBuilder.create { instance ->
            instance.group(
                AlignmentPoint.CODEC.fieldOf("alignmentPoint").forGetter { it.alignmentPoint },
                AlignmentPoint.CODEC.optionalFieldOf("previousAlignment").forGetter {
                    java.util.Optional.ofNullable(it.previousAlignment)
                }
            ).apply(instance) { point, prev ->
                CharacterAlignment(point, prev.orElse(null))
            }
        }
    }
}

/**
 * Убеждение персонажа.
 *
 * @property id уникальный ID убеждения (из датапака или сгенерированный)
 * @property description текстовое описание убеждения
 * @property isShaken пошатнулось ли убеждение (не соответствует мировоззрению)
 */
data class Belief(
    val id: String,
    val description: String,
    val isShaken: Boolean = false
) {
    companion object {
        val CODEC: Codec<Belief> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter { it.id },
                Codec.STRING.fieldOf("description").forGetter { it.description },
                Codec.BOOL.fieldOf("isShaken").forGetter { it.isShaken }
            ).apply(instance, ::Belief)
        }
    }
}

/**
 * Слабость персонажа.
 *
 * @property id ID слабости (из датапака)
 * @property strength текущая "сила" слабости (насколько она выражена)
 */
data class Weakness(
    val id: Identifier,
    val strength: Int = 0
) {
    companion object {
        val CODEC: Codec<Weakness> = RecordCodecBuilder.create { instance ->
            instance.group(
                Identifier.CODEC.fieldOf("id").forGetter { it.id },
                Codec.INT.fieldOf("strength").forGetter { it.strength }
            ).apply(instance, ::Weakness)
        }
    }
}

/**
 * Мотивация персонажа.
 *
 * Мотивации — это движущие силы персонажа.
 * Цели (Goals) — это личные квесты, они живут в системе квестов.
 *
 * @property id ID мотивации (из датапака)
 * @property description описание мотивации
 * @property isActive активна ли мотивация сейчас
 */
data class Motivation(
    val id: Identifier,
    val description: String,
    val isActive: Boolean = true
) {
    companion object {
        val CODEC: Codec<Motivation> = RecordCodecBuilder.create { instance ->
            instance.group(
                Identifier.CODEC.fieldOf("id").forGetter { it.id },
                Codec.STRING.fieldOf("description").forGetter { it.description },
                Codec.BOOL.fieldOf("isActive").forGetter { it.isActive }
            ).apply(instance, ::Motivation)
        }
    }
}
