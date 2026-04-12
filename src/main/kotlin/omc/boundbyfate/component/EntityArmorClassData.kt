package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Stores the computed Armor Class for an entity.
 *
 * AC is recalculated whenever equipment changes or stats change.
 * Stored as a component so it can be read quickly during attack resolution.
 *
 * @property baseAc The computed AC value (10 + armor + dex_capped + shield)
 * @property strRequirementMet Whether the STR requirement for worn armor is met.
 *           If false, movement speed penalty is applied.
 */
data class EntityArmorClassData(
    val baseAc: Int = 10,
    val strRequirementMet: Boolean = true
) {
    companion object {
        val CODEC: Codec<EntityArmorClassData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("baseAc").forGetter { it.baseAc },
                Codec.BOOL.optionalFieldOf("strRequirementMet", true).forGetter { it.strRequirementMet }
            ).apply(instance, ::EntityArmorClassData)
        }
    }
}
