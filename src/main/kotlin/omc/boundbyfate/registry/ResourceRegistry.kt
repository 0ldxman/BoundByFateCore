package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.resource.RecoveryType
import omc.boundbyfate.api.resource.ResourceDefinition
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry for all resource pool definitions.
 *
 * Thread-safe singleton. Resources must be registered during mod initialization.
 *
 * Usage:
 * ```kotlin
 * val MY_RESOURCE = ResourceDefinition(
 *     id = Identifier("mymod", "blood_maledict"),
 *     displayName = "Проклятие крови",
 *     recoveryType = RecoveryType.SHORT_REST,
 *     defaultMaximum = 1
 * )
 * ResourceRegistry.register(MY_RESOURCE)
 * ```
 */
object ResourceRegistry {
    private val resources = ConcurrentHashMap<Identifier, ResourceDefinition>()

    fun register(definition: ResourceDefinition): ResourceDefinition {
        val existing = resources.putIfAbsent(definition.id, definition)
        require(existing == null) { "Resource with ID ${definition.id} is already registered" }
        return definition
    }

    fun get(id: Identifier): ResourceDefinition? = resources[id]

    fun getOrThrow(id: Identifier): ResourceDefinition =
        get(id) ?: throw IllegalArgumentException("Unknown resource ID: $id")

    fun contains(id: Identifier): Boolean = resources.containsKey(id)

    fun getAll(): Collection<ResourceDefinition> = resources.values.toList()

    fun getAllByRecovery(type: RecoveryType): Collection<ResourceDefinition> =
        resources.values.filter { it.recoveryType == type }

    val size: Int get() = resources.size
}
