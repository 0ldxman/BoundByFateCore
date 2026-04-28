package omc.boundbyfate.api.race

import net.minecraft.util.Identifier

/**
 * Итоговые данные расы после мёрджа с подрасой.
 *
 * Создаётся [RaceMerger] и содержит всё что персонаж получает от расы.
 * Используется системами при создании персонажа и применении расовых бонусов.
 *
 * ## Правила мёрджа
 *
 * - Гранты **Speed** и **Size** — подраса переопределяет родителя (берётся подраса)
 * - Гранты **StatBonus**, **Feature**, **Language**, **Ability** — складываются
 * - Выборы — складываются (игрок делает все выборы из обеих)
 * - [modelScale] — подраса переопределяет родителя если задан
 *
 * @property raceId ID корневой расы
 * @property subraceId ID подрасы (null если нет)
 * @property grants итоговый список грантов
 * @property choices итоговый список выборов
 * @property modelScale итоговый масштаб модели для Pekhui
 * @property tags объединённые теги расы и подрасы
 */
data class ResolvedRace(
    val raceId: Identifier,
    val subraceId: Identifier? = null,
    val grants: List<RaceGrant>,
    val choices: List<RaceChoice>,
    val modelScale: Float,
    val tags: List<String> = emptyList()
) {
    // ── Удобные геттеры ───────────────────────────────────────────────────

    val size: CreatureSize?
        get() = grants.filterIsInstance<RaceGrant.Size>().firstOrNull()?.size

    val speeds: Map<MovementType, Int>
        get() = grants.filterIsInstance<RaceGrant.Speed>()
            .associate { it.movement to it.value }

    val walkSpeed: Int
        get() = speeds[MovementType.WALK] ?: 30

    val statBonuses: List<RaceGrant.StatBonus>
        get() = grants.filterIsInstance<RaceGrant.StatBonus>()

    val features: List<Identifier>
        get() = grants.filterIsInstance<RaceGrant.Feature>().map { it.id }

    val languages: List<Identifier>
        get() = grants.filterIsInstance<RaceGrant.Language>().map { it.id }

    val abilities: List<Identifier>
        get() = grants.filterIsInstance<RaceGrant.Ability>().map { it.id }

    val statBonusChoices: List<RaceChoice.StatBonus>
        get() = choices.filterIsInstance<RaceChoice.StatBonus>()

    val languageChoices: List<RaceChoice.Language>
        get() = choices.filterIsInstance<RaceChoice.Language>()

    val featureChoices: List<RaceChoice.Feature>
        get() = choices.filterIsInstance<RaceChoice.Feature>()

    fun hasTag(tag: String): Boolean = tag in tags
}
