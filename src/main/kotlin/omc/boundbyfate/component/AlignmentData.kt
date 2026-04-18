package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import omc.boundbyfate.api.identity.Alignment
import omc.boundbyfate.api.identity.AlignmentCoordinates

/**
 * Stores player's alignment data including coordinates and history.
 */
data class AlignmentData(
    val coordinates: AlignmentCoordinates = AlignmentCoordinates(0, 0),
    val history: List<AlignmentShift> = emptyList()
) {
    val currentAlignment: Alignment
        get() = coordinates.getAlignment()

    companion object {
        val CODEC: Codec<AlignmentData> = RecordCodecBuilder.create { instance ->
            instance.group(
                AlignmentCoordinates.CODEC.fieldOf("coordinates").forGetter(AlignmentData::coordinates),
                Codec.list(AlignmentShift.CODEC).optionalFieldOf("history", emptyList()).forGetter(AlignmentData::history)
            ).apply(instance, ::AlignmentData)
        }
    }
}

/**
 * Records a single alignment shift event.
 */
data class AlignmentShift(
    val timestamp: Long,
    val reason: String,
    val lawChaosChange: Int,
    val goodEvilChange: Int,
    val oldAlignment: Alignment,
    val newAlignment: Alignment
) {
    companion object {
        val CODEC: Codec<AlignmentShift> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.LONG.fieldOf("timestamp").forGetter(AlignmentShift::timestamp),
                Codec.STRING.fieldOf("reason").forGetter(AlignmentShift::reason),
                Codec.INT.fieldOf("lawChaosChange").forGetter(AlignmentShift::lawChaosChange),
                Codec.INT.fieldOf("goodEvilChange").forGetter(AlignmentShift::goodEvilChange),
                Codec.STRING.fieldOf("oldAlignment").xmap(
                    { Alignment.valueOf(it) },
                    { it.name }
                ).forGetter(AlignmentShift::oldAlignment),
                Codec.STRING.fieldOf("newAlignment").xmap(
                    { Alignment.valueOf(it) },
                    { it.name }
                ).forGetter(AlignmentShift::newAlignment)
            ).apply(instance, ::AlignmentShift)
        }
    }
}
