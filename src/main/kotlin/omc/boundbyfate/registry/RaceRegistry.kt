package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.race.RaceDefinition
import omc.boundbyfate.api.race.SubraceDefinition
import java.util.concurrent.ConcurrentHashMap

object RaceRegistry {
    private val races = ConcurrentHashMap<Identifier, RaceDefinition>()
    private val subraces = ConcurrentHashMap<Identifier, SubraceDefinition>()

    fun registerRace(definition: RaceDefinition): RaceDefinition {
        val existing = races.putIfAbsent(definition.id, definition)
        require(existing == null) { "Race ${definition.id} is already registered" }
        return definition
    }

    fun registerSubrace(definition: SubraceDefinition): SubraceDefinition {
        val existing = subraces.putIfAbsent(definition.id, definition)
        require(existing == null) { "Subrace ${definition.id} is already registered" }
        return definition
    }

    fun getRace(id: Identifier): RaceDefinition? = races[id]
    fun getSubrace(id: Identifier): SubraceDefinition? = subraces[id]
    fun getAllRaces(): Collection<RaceDefinition> = races.values.toList()
    fun getSubracesFor(raceId: Identifier): Collection<SubraceDefinition> =
        subraces.values.filter { it.parentRace == raceId }

    fun clearAll() { races.clear(); subraces.clear() }

    val raceCount: Int get() = races.size
    val subraceCount: Int get() = subraces.size
}
