package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.race.RaceDefinition
import omc.boundbyfate.api.race.RaceMerger
import omc.boundbyfate.api.race.ResolvedRace
import omc.boundbyfate.registry.core.BbfRegistry

/**
 * Реестр рас.
 *
 * Хранит все [RaceDefinition] загруженные из датапаков.
 * Предоставляет удобные методы для работы с расами и подрасами.
 */
object RaceRegistry : BbfRegistry<RaceDefinition>("races") {

    /**
     * Возвращает все корневые расы (не подрасы).
     */
    fun getRootRaces(): List<RaceDefinition> =
        getAll().filter { !it.isSubrace }

    /**
     * Возвращает все подрасы для данной расы.
     */
    fun getSubraces(raceId: Identifier): List<RaceDefinition> {
        val race = get(raceId) ?: return emptyList()
        return race.subraces.mapNotNull { get(it) }
    }

    /**
     * Возвращает родительскую расу для подрасы.
     */
    fun getParentRace(subraceId: Identifier): RaceDefinition? {
        val subrace = get(subraceId) ?: return null
        return subrace.parentRace?.let { get(it) }
    }

    /**
     * Резолвит итоговую расу персонажа.
     *
     * @param raceId ID корневой расы
     * @param subraceId ID подрасы (null если нет или раса без подрас)
     * @return итоговая раса или null если раса не найдена
     */
    fun resolve(raceId: Identifier, subraceId: Identifier? = null): ResolvedRace? {
        val race = get(raceId) ?: return null

        if (subraceId == null) {
            return RaceMerger.resolve(race)
        }

        val subrace = get(subraceId) ?: return RaceMerger.resolve(race)
        return RaceMerger.merge(race, subrace)
    }

    override fun onRegistrationComplete() {
        super.onRegistrationComplete()
        validateRaceReferences()
    }

    /**
     * Проверяет что все ссылки на подрасы валидны.
     */
    private fun validateRaceReferences() {
        for (race in getAll()) {
            // Проверяем что все подрасы существуют
            for (subraceId in race.subraces) {
                if (!contains(subraceId)) {
                    logger.warn("Race '${race.id}' references unknown subrace '$subraceId'")
                }
            }
            // Проверяем что parent_race существует
            race.parentRace?.let { parentId ->
                if (!contains(parentId)) {
                    logger.warn("Subrace '${race.id}' references unknown parent race '$parentId'")
                }
            }
        }
    }
}
