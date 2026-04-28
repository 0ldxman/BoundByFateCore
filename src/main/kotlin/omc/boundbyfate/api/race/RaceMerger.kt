package omc.boundbyfate.api.race

/**
 * Мёрджит расу и подрасу в итоговый [ResolvedRace].
 *
 * ## Правила мёрджа
 *
 * **Переопределяются** (подраса заменяет родителя):
 * - [RaceGrant.Size] — берётся из подрасы если есть
 * - [RaceGrant.Speed] — для каждого [MovementType] берётся из подрасы если есть
 * - [ResolvedRace.modelScale] — берётся из подрасы если задан
 *
 * **Складываются** (оба источника):
 * - [RaceGrant.StatBonus]
 * - [RaceGrant.Feature]
 * - [RaceGrant.Language]
 * - [RaceGrant.Ability]
 * - [RaceChoice] — все выборы из обеих
 * - Теги
 */
object RaceMerger {

    /**
     * Мёрджит расу без подрасы.
     */
    fun resolve(race: RaceDefinition): ResolvedRace {
        return ResolvedRace(
            raceId = race.id,
            subraceId = null,
            grants = race.grants,
            choices = race.choices,
            modelScale = race.resolvedModelScale(),
            tags = race.tags
        )
    }

    /**
     * Мёрджит расу с подрасой.
     *
     * @param parent корневая раса
     * @param subrace подраса (должна иметь parent_race == parent.id)
     */
    fun merge(parent: RaceDefinition, subrace: RaceDefinition): ResolvedRace {
        val mergedGrants = mergeGrants(parent.grants, subrace.grants)
        val mergedChoices = parent.choices + subrace.choices
        val mergedTags = (parent.tags + subrace.tags).distinct()

        // Масштаб: подраса переопределяет родителя
        val modelScale = subrace.modelSize
            ?: subrace.sizeGrants.firstOrNull()?.size?.defaultModelScale
            ?: parent.resolvedModelScale()

        return ResolvedRace(
            raceId = parent.id,
            subraceId = subrace.id,
            grants = mergedGrants,
            choices = mergedChoices,
            modelScale = modelScale,
            tags = mergedTags
        )
    }

    // ── Внутренняя логика мёрджа грантов ─────────────────────────────────

    private fun mergeGrants(
        parentGrants: List<RaceGrant>,
        subraceGrants: List<RaceGrant>
    ): List<RaceGrant> {
        val result = mutableListOf<RaceGrant>()

        // Size — подраса переопределяет
        val subraceSize = subraceGrants.filterIsInstance<RaceGrant.Size>().firstOrNull()
        val parentSize = parentGrants.filterIsInstance<RaceGrant.Size>().firstOrNull()
        (subraceSize ?: parentSize)?.let { result.add(it) }

        // Speed — для каждого MovementType подраса переопределяет
        val parentSpeeds = parentGrants.filterIsInstance<RaceGrant.Speed>()
            .associateBy { it.movement }
        val subraceSpeeds = subraceGrants.filterIsInstance<RaceGrant.Speed>()
            .associateBy { it.movement }
        val mergedSpeeds = parentSpeeds + subraceSpeeds // subrace overrides parent
        result.addAll(mergedSpeeds.values)

        // StatBonus — складываются
        result.addAll(parentGrants.filterIsInstance<RaceGrant.StatBonus>())
        result.addAll(subraceGrants.filterIsInstance<RaceGrant.StatBonus>())

        // Feature — складываются (без дублей)
        val parentFeatures = parentGrants.filterIsInstance<RaceGrant.Feature>()
        val subraceFeatures = subraceGrants.filterIsInstance<RaceGrant.Feature>()
        val allFeatures = (parentFeatures + subraceFeatures).distinctBy { it.id }
        result.addAll(allFeatures)

        // Language — складываются (без дублей)
        val parentLanguages = parentGrants.filterIsInstance<RaceGrant.Language>()
        val subraceLanguages = subraceGrants.filterIsInstance<RaceGrant.Language>()
        val allLanguages = (parentLanguages + subraceLanguages).distinctBy { it.id }
        result.addAll(allLanguages)

        // Ability — складываются (без дублей)
        val parentAbilities = parentGrants.filterIsInstance<RaceGrant.Ability>()
        val subraceAbilities = subraceGrants.filterIsInstance<RaceGrant.Ability>()
        val allAbilities = (parentAbilities + subraceAbilities).distinctBy { it.id }
        result.addAll(allAbilities)

        return result
    }
}
