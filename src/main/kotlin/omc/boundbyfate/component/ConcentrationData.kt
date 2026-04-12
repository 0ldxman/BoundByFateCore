package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier

/**
 * Данные концентрации игрока.
 * 
 * Хранит информацию об активном заклинании концентрации.
 */
data class ConcentrationData(
    /** ID заклинания, на котором концентрируется игрок */
    val abilityId: Identifier,
    
    /** Тик начала концентрации */
    val startTick: Long
) {
    companion object {
        val CODEC: Codec<ConcentrationData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Identifier.CODEC.fieldOf("abilityId").forGetter { it.abilityId },
                Codec.LONG.fieldOf("startTick").forGetter { it.startTick }
            ).apply(instance, ::ConcentrationData)
        }
    }
}
