package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Stores player scale (size).
 * - baseScale: scale from race (e.g., 1.0 for human, 0.8 for dwarf)
 * - modifierScale: GM-applied modifier (e.g., +0.3 or -0.2)
 * - Total scale = baseScale + modifierScale
 */
data class PlayerScaleData(
    val baseScale: Float = 1.0f,
    val modifierScale: Float = 0.0f
) {
    fun totalScale(): Float = baseScale + modifierScale

    companion object {
        val CODEC: Codec<PlayerScaleData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.FLOAT.fieldOf("baseScale").forGetter { it.baseScale },
                Codec.FLOAT.fieldOf("modifierScale").forGetter { it.modifierScale }
            ).apply(instance, ::PlayerScaleData)
        }
    }
}
