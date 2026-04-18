package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Stores player speed in feet.
 * - baseSpeedFt: speed from race (e.g., 30 for human, 25 for dwarf)
 * - modifierFt: GM-applied modifier (e.g., +5 or -5)
 * - Total speed = baseSpeedFt + modifierFt
 */
data class PlayerSpeedData(
    val baseSpeedFt: Int = 30,
    val modifierFt: Int = 0
) {
    fun totalSpeedFt(): Int = baseSpeedFt + modifierFt

    companion object {
        val CODEC: Codec<PlayerSpeedData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("baseSpeedFt").forGetter { it.baseSpeedFt },
                Codec.INT.fieldOf("modifierFt").forGetter { it.modifierFt }
            ).apply(instance, ::PlayerSpeedData)
        }
    }
}
