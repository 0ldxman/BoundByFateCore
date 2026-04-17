package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Stores darkvision range for an entity.
 * @property rangeFt Range in D&D feet (60 = standard darkvision)
 */
data class DarkvisionData(
    val rangeFt: Int = 60
) {
    /** Range in Minecraft blocks (5ft = 1.5m ≈ 1.5 blocks, 60ft ≈ 18 blocks) */
    val rangeBlocks: Int get() = (rangeFt / 5.0 * 1.5).toInt()

    companion object {
        val CODEC: Codec<DarkvisionData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("rangeFt").forGetter { it.rangeFt }
            ).apply(instance, ::DarkvisionData)
        }
    }
}
