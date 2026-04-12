package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier

/**
 * Stores a player's race and subrace assignment.
 *
 * @property raceId The player's race identifier
 * @property subraceId The player's subrace identifier (null if no subrace)
 */
data class PlayerRaceData(
    val raceId: Identifier,
    val subraceId: Identifier? = null
) {
    companion object {
        val CODEC: Codec<PlayerRaceData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Identifier.CODEC.fieldOf("raceId").forGetter { it.raceId },
                Identifier.CODEC.optionalFieldOf("subraceId").forGetter {
                    java.util.Optional.ofNullable(it.subraceId)
                }
            ).apply(instance) { raceId, subraceId ->
                PlayerRaceData(raceId, subraceId.orElse(null))
            }
        }
    }
}
