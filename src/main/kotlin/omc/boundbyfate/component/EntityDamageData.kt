package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier

/**
 * Stores damage resistances, immunities and vulnerabilities for an entity.
 *
 * The modifier is a float multiplier applied to incoming damage:
 * - 0.0 = immunity (no damage)
 * - 0.5 = resistance (half damage)
 * - 1.0 = normal (default, no entry needed)
 * - 2.0 = vulnerability (double damage)
 *
 * Works with any DamageType identifier (both minecraft:* and boundbyfate-core:*).
 *
 * @property resistances Map of damage type ID to damage multiplier
 */
data class EntityDamageData(
    val resistances: Map<Identifier, Float> = emptyMap()
) {
    /**
     * Returns the damage multiplier for a given damage type.
     * Returns 1.0 if no entry exists (normal damage).
     */
    fun getModifier(damageTypeId: Identifier): Float =
        resistances[damageTypeId] ?: 1.0f

    /**
     * Returns true if the entity is immune to this damage type.
     */
    fun isImmune(damageTypeId: Identifier): Boolean =
        (resistances[damageTypeId] ?: 1.0f) == 0.0f

    /**
     * Returns a new EntityDamageData with the given resistance set.
     *
     * @param damageTypeId The damage type identifier
     * @param modifier Damage multiplier (0.0 = immune, 0.5 = resist, 1.0 = normal, 2.0 = vulnerable)
     */
    fun withResistance(damageTypeId: Identifier, modifier: Float): EntityDamageData {
        require(modifier >= 0.0f) { "Damage modifier cannot be negative" }
        return copy(resistances = resistances + (damageTypeId to modifier))
    }

    /**
     * Returns a new EntityDamageData with the resistance removed (back to normal).
     */
    fun withoutResistance(damageTypeId: Identifier): EntityDamageData =
        copy(resistances = resistances - damageTypeId)

    companion object {
        val CODEC: Codec<EntityDamageData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.unboundedMap(Identifier.CODEC, Codec.FLOAT)
                    .optionalFieldOf("resistances", emptyMap())
                    .forGetter { it.resistances }
            ).apply(instance, ::EntityDamageData)
        }
    }
}
