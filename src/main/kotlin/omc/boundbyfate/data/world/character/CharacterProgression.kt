package omc.boundbyfate.data.world.character

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Данные прогрессии персонажа.
 *
 * Уровень вынесен отдельно от класса — на него ссылаются многие системы
 * (урон, условия способностей, масштабирование эффектов и т.д.).
 *
 * @property level текущий уровень персонажа (1–20)
 * @property experienceInLevel опыт накопленный на текущем уровне
 */
data class CharacterProgression(
    val level: Int = 1,
    val experienceInLevel: Int = 0
) {
    companion object {
        val CODEC: Codec<CharacterProgression> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("level").forGetter { it.level },
                Codec.INT.fieldOf("experienceInLevel").forGetter { it.experienceInLevel }
            ).apply(instance, ::CharacterProgression)
        }
    }
}
