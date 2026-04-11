package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier

/**
 * Stores damage resistances, immunities and vulnerabilities for an entity.
 *
 * Resistances are stored per source (race, equipment, ability) to allow
 * multiple sources to grant the same resistance without overwriting each other.
 * The effective modifier for a damage type is the MINIMUM across all sources
 * (most protective wins).
 *
 * Modifier values:
 * - 0.0 = immunity (no damage)
 * - 0.5 = resistance (half damage)
 * - 1.0 = normal (default, no entry needed)
 * - 2.0 = vulnerability (double damage)
 *
 * @property sources Map of sourceId -> (damageTypeId -> modifier)
 */
data class EntityDamageData(
    val sources: Map<Identifier, Map<Identifier, Float>> = emptyMap()
) {
    /**
     * Returns the effective damage multiplier for a given damage type.
     * Takes the minimum modifier across all sources (most protective wins).
     * Returns 1.0 if no entry exists (normal damage).
     */
    fun getModifier(damageTypeId: Identifier): Float {
        var min = 1.0f
        for ((_, resistances) in sources) {
            val modifier = resistances[damageTypeId] ?: continue
            if (modifier < min) min = modifier
        }
        return min
    }

    /**
     * Returns true if the entity is immune to this damage type from any source.
     */
    fun isImmune(damageTypeId: Identifier): Boolean = getModifier(damageTypeId) == 0.0f

    /**
     * Adds or updates a resistance from a specific source.
     *
     * @param sourceId Who grants this resistance (e.g. "boundbyfate-core:race_dwarf")
     * @param damageTypeId The damage type identifier
     * @param modifier Damage multiplier (0.0=immune, 0.5=resist, 1.0=normal, 2.0=vulnerable)
     */
    fun withResistance(sourceId: Identifier, damageTypeId: Identifier, modifier: Float): EntityDamageData {
        require(modifier >= 0.0f) { "Damage modifier cannot be negative" }
        val sourceMap = (sources[sourceId] ?: emptyMap()) + (damageTypeId to modifier)
        return copy(sources = sources + (sourceId to sourceMap))
    }

    /**
     * Removes a specific resistance from a source.
     */
    fun withoutResistance(sourceId: Identifier, damageTypeId: Identifier): EntityDamageData {
        val sourceMap = sources[sourceId] ?: return this
        val updated = sourceMap - damageTypeId
        return if (updated.isEmpty()) {
            copy(sources = sources - sourceId)
        } else {
            copy(sources = sources + (sourceId to updated))
        }
    }

    /**
     * Removes ALL resistances from a source (e.g. when unequipping an item).
     */
    fun withoutSource(sourceId: Identifier): EntityDamageData =
        copy(sources = sources - sourceId)

    companion object {
        val CODEC: Codec<EntityDamageData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.unboundedMap(
                    Identifier.CODEC,
                    Codec.unboundedMap(Identifier.CODEC, Codec.FLOAT)
                )
                    .optionalFieldOf("sources", emptyMap())
                    .forGetter { it.sources }
            ).apply(instance, ::EntityDamageData)
        }
    }
}
