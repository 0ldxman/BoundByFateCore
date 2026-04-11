package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.api.resource.RecoveryType
import omc.boundbyfate.registry.ResourceRegistry

/**
 * Immutable data class storing all resource pool values for an entity.
 *
 * Attached to entities via Fabric Data Attachment API.
 * All modifications return new instances (immutable pattern).
 *
 * @property pools Map of resource ID to current/maximum values
 */
data class EntityResourceData(
    val pools: Map<Identifier, ResourcePool> = emptyMap()
) {
    /**
     * A single resource pool entry (current and maximum values).
     */
    data class ResourcePool(
        val current: Int,
        val maximum: Int
    ) {
        val isEmpty: Boolean get() = current <= 0
        val isFull: Boolean get() = current >= maximum

        companion object {
            val CODEC: Codec<ResourcePool> = RecordCodecBuilder.create { instance ->
                instance.group(
                    Codec.INT.fieldOf("current").forGetter { it.current },
                    Codec.INT.fieldOf("maximum").forGetter { it.maximum }
                ).apply(instance, ::ResourcePool)
            }
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    fun getPool(id: Identifier): ResourcePool? = pools[id]

    fun getCurrent(id: Identifier): Int = pools[id]?.current ?: 0

    fun getMaximum(id: Identifier): Int = pools[id]?.maximum ?: 0

    fun hasPool(id: Identifier): Boolean = pools.containsKey(id)

    fun canSpend(id: Identifier, amount: Int): Boolean =
        (pools[id]?.current ?: 0) >= amount

    // ── Write (all return new instances) ─────────────────────────────────────

    /**
     * Adds or replaces a resource pool with given maximum.
     * Current value is set to maximum (full on creation).
     */
    fun withPool(id: Identifier, maximum: Int): EntityResourceData {
        ResourceRegistry.getOrThrow(id) // Validate exists
        val pool = ResourcePool(current = maximum.coerceAtLeast(0), maximum = maximum.coerceAtLeast(0))
        return copy(pools = pools + (id to pool))
    }

    /**
     * Removes a resource pool entirely.
     */
    fun withoutPool(id: Identifier): EntityResourceData =
        copy(pools = pools - id)

    /**
     * Updates the maximum for a pool, clamping current if needed.
     */
    fun withMaximum(id: Identifier, newMax: Int): EntityResourceData {
        val existing = pools[id] ?: return this
        val clamped = existing.current.coerceAtMost(newMax)
        return copy(pools = pools + (id to existing.copy(current = clamped, maximum = newMax)))
    }

    /**
     * Spends [amount] from a pool.
     * Returns null if insufficient resources.
     */
    fun spend(id: Identifier, amount: Int): EntityResourceData? {
        val pool = pools[id] ?: return null
        if (pool.current < amount) return null
        return copy(pools = pools + (id to pool.copy(current = pool.current - amount)))
    }

    /**
     * Restores [amount] to a pool, capped at maximum.
     */
    fun restore(id: Identifier, amount: Int): EntityResourceData {
        val pool = pools[id] ?: return this
        val newCurrent = (pool.current + amount).coerceAtMost(pool.maximum)
        return copy(pools = pools + (id to pool.copy(current = newCurrent)))
    }

    /**
     * Restores a pool to its maximum.
     */
    fun restoreFull(id: Identifier): EntityResourceData {
        val pool = pools[id] ?: return this
        return copy(pools = pools + (id to pool.copy(current = pool.maximum)))
    }

    /**
     * Restores all pools that match the given recovery type to their maximum.
     */
    fun restoreByRecovery(type: RecoveryType): EntityResourceData {
        val updated = pools.toMutableMap()
        for ((id, pool) in pools) {
            val definition = ResourceRegistry.get(id) ?: continue
            if (definition.recoveryType == type) {
                updated[id] = pool.copy(current = pool.maximum)
            }
        }
        return copy(pools = updated)
    }

    /**
     * Restores all pools to their maximum regardless of recovery type.
     */
    fun restoreAll(): EntityResourceData {
        val updated = pools.mapValues { (_, pool) -> pool.copy(current = pool.maximum) }
        return copy(pools = updated)
    }

    companion object {
        val CODEC: Codec<EntityResourceData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.unboundedMap(Identifier.CODEC, ResourcePool.CODEC)
                    .optionalFieldOf("pools", emptyMap())
                    .forGetter { it.pools }
            ).apply(instance, ::EntityResourceData)
        }
    }
}
