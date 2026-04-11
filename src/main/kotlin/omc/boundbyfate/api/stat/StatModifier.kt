package omc.boundbyfate.api.stat

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier

/**
 * Represents a modification to a stat value from an external source.
 *
 * Modifiers are stacked and applied when computing the final [StatValue].
 *
 * @property sourceId Identifier of the source (e.g., "boundbyfate-core:race_elf", "boundbyfate-core:level_bonus")
 * @property type Type of modification (FLAT or OVERRIDE)
 * @property value Numeric value of the modification
 */
data class StatModifier(
    val sourceId: Identifier,
    val type: ModifierType,
    val value: Int
) {
    companion object {
        /**
         * Codec for serializing/deserializing StatModifier.
         */
        val CODEC: Codec<StatModifier> = RecordCodecBuilder.create { instance ->
            instance.group(
                Identifier.CODEC.fieldOf("source").forGetter { it.sourceId },
                ModifierType.CODEC.fieldOf("type").forGetter { it.type },
                Codec.INT.fieldOf("value").forGetter { it.value }
            ).apply(instance, ::StatModifier)
        }
    }
}

/**
 * Type of stat modifier.
 */
enum class ModifierType {
    /**
     * Adds value to the total (additive).
     * Multiple FLAT modifiers stack.
     */
    FLAT,
    
    /**
     * Replaces the base value (for admin commands).
     * Last OVERRIDE wins if multiple are present.
     */
    OVERRIDE;
    
    companion object {
        /**
         * Codec for serializing/deserializing ModifierType.
         */
        val CODEC: Codec<ModifierType> = Codec.STRING.xmap(
            { ModifierType.valueOf(it.uppercase()) },
            { it.name.lowercase() }
        )
    }
}
