package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.proficiency.ProficiencyDefinition
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry for all proficiency definitions.
 * Populated by [omc.boundbyfate.config.ProficiencyDatapackLoader] on server start.
 */
object ProficiencyRegistry {
    private val proficiencies = ConcurrentHashMap<Identifier, ProficiencyDefinition>()

    fun register(definition: ProficiencyDefinition): ProficiencyDefinition {
        val existing = proficiencies.putIfAbsent(definition.id, definition)
        require(existing == null) { "Proficiency ${definition.id} is already registered" }
        return definition
    }

    fun get(id: Identifier): ProficiencyDefinition? = proficiencies[id]

    fun getAll(): Collection<ProficiencyDefinition> = proficiencies.values.toList()

    fun clearAll() = proficiencies.clear()

    val size: Int get() = proficiencies.size
}
