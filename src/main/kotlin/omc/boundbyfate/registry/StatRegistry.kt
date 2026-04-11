package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.stat.StatDefinition
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry for all stat definitions.
 *
 * Thread-safe singleton that manages registration and lookup of [StatDefinition]s.
 * Stats must be registered during mod initialization before configs are loaded.
 *
 * Example usage:
 * ```kotlin
 * val customStat = StatDefinition(
 *     id = Identifier("mymod", "luck"),
 *     shortName = "LCK",
 *     displayName = "Luck"
 * )
 * StatRegistry.register(customStat)
 * ```
 */
object StatRegistry {
    private val stats = ConcurrentHashMap<Identifier, StatDefinition>()
    
    /**
     * Registers a stat definition.
     *
     * @param definition The stat definition to register
     * @return The registered definition (for chaining)
     * @throws IllegalArgumentException if a stat with this ID is already registered
     */
    fun register(definition: StatDefinition): StatDefinition {
        val existing = stats.putIfAbsent(definition.id, definition)
        require(existing == null) {
            "Stat with ID ${definition.id} is already registered"
        }
        return definition
    }
    
    /**
     * Gets a stat definition by ID.
     *
     * @param id The stat identifier
     * @return The stat definition, or null if not found
     */
    fun get(id: Identifier): StatDefinition? = stats[id]
    
    /**
     * Gets a stat definition by ID, throwing if not found.
     *
     * @param id The stat identifier
     * @return The stat definition
     * @throws IllegalArgumentException if stat not found
     */
    fun getOrThrow(id: Identifier): StatDefinition {
        return get(id) ?: throw IllegalArgumentException("Unknown stat ID: $id")
    }
    
    /**
     * Gets all registered stat definitions.
     *
     * @return Immutable collection of all stats
     */
    fun getAll(): Collection<StatDefinition> = stats.values.toList()
    
    /**
     * Checks if a stat is registered.
     *
     * @param id The stat identifier
     * @return true if registered, false otherwise
     */
    fun contains(id: Identifier): Boolean = stats.containsKey(id)
    
    /**
     * Gets the number of registered stats.
     */
    val size: Int get() = stats.size
}
