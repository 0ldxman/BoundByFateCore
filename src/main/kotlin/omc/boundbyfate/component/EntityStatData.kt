package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.api.stat.StatModifier
import omc.boundbyfate.api.stat.StatValue
import omc.boundbyfate.registry.StatRegistry

/**
 * Immutable data class storing stat values and modifiers for an entity.
 *
 * Attached to entities (players/mobs) via Fabric Data Attachment API.
 * All modifications return new instances (immutable pattern).
 *
 * @property baseStats Map of stat ID to base value
 * @property modifiers Map of stat ID to list of modifiers
 */
data class EntityStatData(
    val baseStats: Map<Identifier, Int> = emptyMap(),
    val modifiers: Map<Identifier, List<StatModifier>> = emptyMap()
) {
    /**
     * Computes the final StatValue for a given stat.
     *
     * If the stat is not in baseStats, uses the StatDefinition's defaultValue.
     * Applies all modifiers and calculates D&D modifier.
     *
     * @param statId The stat identifier
     * @return Computed StatValue
     * @throws IllegalArgumentException if statId is not registered
     */
    fun getStatValue(statId: Identifier): StatValue {
        val definition = StatRegistry.getOrThrow(statId)
        val base = baseStats[statId] ?: definition.defaultValue
        val mods = modifiers[statId] ?: emptyList()
        
        return StatValue.compute(base, mods, definition)
    }
    
    /**
     * Returns a new EntityStatData with updated base value for a stat.
     *
     * @param statId The stat identifier
     * @param value New base value (will be clamped to stat's range)
     * @return New EntityStatData instance
     */
    fun withBase(statId: Identifier, value: Int): EntityStatData {
        val definition = StatRegistry.getOrThrow(statId)
        val clampedValue = definition.clamp(value)
        
        return copy(baseStats = baseStats + (statId to clampedValue))
    }
    
    /**
     * Returns a new EntityStatData with an added modifier.
     *
     * @param statId The stat identifier
     * @param modifier The modifier to add
     * @return New EntityStatData instance
     */
    fun withModifier(statId: Identifier, modifier: StatModifier): EntityStatData {
        StatRegistry.getOrThrow(statId) // Validate stat exists
        
        val currentMods = modifiers[statId] ?: emptyList()
        val newMods = currentMods + modifier
        
        return copy(modifiers = modifiers + (statId to newMods))
    }
    
    /**
     * Returns a new EntityStatData with all modifiers from a source removed.
     *
     * @param sourceId The source identifier to remove
     * @return New EntityStatData instance
     */
    fun withoutModifiersFrom(sourceId: Identifier): EntityStatData {
        val newModifiers = modifiers.mapValues { (_, mods) ->
            mods.filterNot { it.sourceId == sourceId }
        }.filterValues { it.isNotEmpty() }
        
        return copy(modifiers = newModifiers)
    }
    
    /**
     * Gets all computed stat values.
     *
     * @return Map of stat ID to computed StatValue
     */
    fun getAllStats(): Map<Identifier, StatValue> {
        return StatRegistry.getAll().associate { definition ->
            definition.id to getStatValue(definition.id)
        }
    }
    
    companion object {
        /**
         * Codec for serializing/deserializing EntityStatData.
         */
        val CODEC: Codec<EntityStatData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.unboundedMap(Identifier.CODEC, Codec.INT)
                    .optionalFieldOf("baseStats", emptyMap())
                    .forGetter { it.baseStats },
                Codec.unboundedMap(
                    Identifier.CODEC,
                    StatModifier.CODEC.listOf()
                )
                    .optionalFieldOf("modifiers", emptyMap())
                    .forGetter { it.modifiers }
            ).apply(instance, ::EntityStatData)
        }
        
        /**
         * Creates EntityStatData from a map of base stat values.
         *
         * @param baseStats Map of stat ID to base value
         * @return New EntityStatData instance
         */
        fun fromBaseStats(baseStats: Map<Identifier, Int>): EntityStatData {
            // Validate and clamp all values
            val validatedStats = baseStats.mapValues { (statId, value) ->
                val definition = StatRegistry.getOrThrow(statId)
                definition.clamp(value)
            }
            
            return EntityStatData(baseStats = validatedStats)
        }
    }
}
