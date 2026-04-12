package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.feat.FeatDefinition
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry for feat definitions.
 * Populated by FeatDatapackLoader on server start.
 */
object FeatRegistry {
    private val feats = ConcurrentHashMap<Identifier, FeatDefinition>()

    fun register(definition: FeatDefinition): FeatDefinition {
        val existing = feats.putIfAbsent(definition.id, definition)
        require(existing == null) { "Feat ${definition.id} is already registered" }
        return definition
    }

    fun get(id: Identifier): FeatDefinition? = feats[id]

    fun getOrThrow(id: Identifier): FeatDefinition =
        get(id) ?: throw IllegalArgumentException("Unknown feat ID: $id")

    fun getAll(): Collection<FeatDefinition> = feats.values.toList()

    fun clearAll() = feats.clear()

    val size: Int get() = feats.size
}
