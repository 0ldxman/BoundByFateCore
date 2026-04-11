package omc.boundbyfate.config

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier

/**
 * Mob stat configuration loaded from JSON file.
 *
 * Defines base stats for a specific mob type.
 * Loaded from `world/boundbyfate/mobs/{mobType}.json`.
 *
 * Example JSON:
 * ```json
 * {
 *   "mobTypeId": "minecraft:zombie",
 *   "baseStats": {
 *     "boundbyfate-core:strength": 13,
 *     "boundbyfate-core:constitution": 15,
 *     "boundbyfate-core:dexterity": 8
 *   }
 * }
 * ```
 *
 * @property mobTypeId Entity type identifier (e.g., "minecraft:zombie")
 * @property baseStats Map of stat ID to base value
 */
data class MobStatProfile(
    val mobTypeId: Identifier,
    val baseStats: Map<Identifier, Int>
) {
    companion object {
        /**
         * Codec for JSON serialization/deserialization.
         */
        val CODEC: Codec<MobStatProfile> = RecordCodecBuilder.create { instance ->
            instance.group(
                Identifier.CODEC.fieldOf("mobTypeId").forGetter { it.mobTypeId },
                Codec.unboundedMap(Identifier.CODEC, Codec.INT)
                    .fieldOf("baseStats")
                    .forGetter { it.baseStats }
            ).apply(instance, ::MobStatProfile)
        }
    }
}
