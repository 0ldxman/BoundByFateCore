package omc.boundbyfate.api.character

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier

/**
 * Расовые данные персонажа.
 *
 * @property raceId ID расы из реестра рас
 * @property subraceId ID подрасы (null если нет)
 * @property gender пол персонажа (свободная строка — не enum, т.к. расы могут иметь свои варианты)
 * @property dateOfBirth дата рождения в игровом летоисчислении (свободная строка)
 */
data class CharacterRace(
    val raceId: Identifier,
    val subraceId: Identifier? = null,
    val gender: String = "",
    val dateOfBirth: String = ""
) {
    companion object {
        val CODEC: Codec<CharacterRace> = RecordCodecBuilder.create { instance ->
            instance.group(
                Identifier.CODEC.fieldOf("raceId").forGetter { it.raceId },
                Identifier.CODEC.optionalFieldOf("subraceId").forGetter {
                    java.util.Optional.ofNullable(it.subraceId)
                },
                Codec.STRING.fieldOf("gender").forGetter { it.gender },
                Codec.STRING.fieldOf("dateOfBirth").forGetter { it.dateOfBirth }
            ).apply(instance) { raceId, subraceId, gender, dateOfBirth ->
                CharacterRace(raceId, subraceId.orElse(null), gender, dateOfBirth)
            }
        }
    }
}
