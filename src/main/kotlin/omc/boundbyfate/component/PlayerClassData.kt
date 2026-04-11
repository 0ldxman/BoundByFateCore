package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier

/**
 * Stores a player's class and subclass assignment.
 *
 * Attached to players via Fabric Data Attachment API.
 *
 * @property classId The player's class identifier
 * @property subclassId The player's subclass identifier (null until chosen)
 * @property classLevel The player's level in this class
 */
data class PlayerClassData(
    val classId: Identifier,
    val subclassId: Identifier? = null,
    val classLevel: Int = 1
) {
    companion object {
        val CODEC: Codec<PlayerClassData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Identifier.CODEC.fieldOf("classId").forGetter { it.classId },
                Identifier.CODEC.optionalFieldOf("subclassId").forGetter {
                    java.util.Optional.ofNullable(it.subclassId)
                },
                Codec.INT.fieldOf("classLevel").forGetter { it.classLevel }
            ).apply(instance) { classId, subclassId, classLevel ->
                PlayerClassData(classId, subclassId.orElse(null), classLevel)
            }
        }
    }
}
