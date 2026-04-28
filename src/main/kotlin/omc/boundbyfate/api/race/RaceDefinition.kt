package omc.boundbyfate.api.race

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.api.core.Definition
import omc.boundbyfate.api.core.Registrable
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Определение расы персонажа.
 *
 * Раса — это список [grants] (что фиксированно даётся) и [choices]
 * (что игрок выбирает при создании персонажа).
 *
 * ## Подрасы
 *
 * Подраса — это отдельная [RaceDefinition] с [parentRace] указывающим на родителя.
 * При выборе подрасы система мёрджит гранты: подраса **переопределяет** гранты
 * родителя того же типа, остальное берётся из родителя.
 *
 * Главная раса содержит список [subraces] — если он не пустой, игрок обязан
 * выбрать подрасу при создании персонажа.
 *
 * ## Размер модели
 *
 * [modelSize] переопределяет дефолтный масштаб Pekhui для данной расы.
 * Если null — используется [CreatureSize.defaultModelScale] из гранта [RaceGrant.Size].
 *
 * ## Примеры JSON
 *
 * ### Раса с подрасами (Dwarf)
 * ```json
 * {
 *   "id": "boundbyfate-core:dwarf",
 *   "subraces": ["boundbyfate-core:mountain_dwarf", "boundbyfate-core:hill_dwarf"],
 *   "grants": [
 *     { "type": "size", "size": "medium" },
 *     { "type": "speed", "movement": "walk", "value": 25 },
 *     { "type": "stat_bonus", "stat": "boundbyfate-core:constitution", "value": 2 },
 *     { "type": "feature", "id": "boundbyfate-core:darkvision_60" },
 *     { "type": "feature", "id": "boundbyfate-core:dwarven_resilience" },
 *     { "type": "language", "id": "boundbyfate-core:lang_common" },
 *     { "type": "language", "id": "boundbyfate-core:lang_dwarvish" }
 *   ],
 *   "choices": []
 * }
 * ```
 *
 * ### Подраса (Mountain Dwarf)
 * ```json
 * {
 *   "id": "boundbyfate-core:mountain_dwarf",
 *   "parent_race": "boundbyfate-core:dwarf",
 *   "grants": [
 *     { "type": "stat_bonus", "stat": "boundbyfate-core:strength", "value": 2 },
 *     { "type": "feature", "id": "boundbyfate-core:dwarven_armor_training" }
 *   ],
 *   "choices": []
 * }
 * ```
 *
 * ### Раса без подрас с выбором (Human)
 * ```json
 * {
 *   "id": "boundbyfate-core:human",
 *   "grants": [
 *     { "type": "size", "size": "medium" },
 *     { "type": "speed", "movement": "walk", "value": 30 },
 *     { "type": "language", "id": "boundbyfate-core:lang_common" }
 *   ],
 *   "choices": [
 *     { "type": "stat_bonus", "count": 6, "from": [], "value": 1 },
 *     { "type": "language", "count": 1, "from": [] }
 *   ]
 * }
 * ```
 *
 * ### Раса с кастомным размером модели (Gnome)
 * ```json
 * {
 *   "id": "boundbyfate-core:gnome",
 *   "model_size": 0.75,
 *   "grants": [
 *     { "type": "size", "size": "small" },
 *     ...
 *   ]
 * }
 * ```
 *
 * @property id уникальный идентификатор
 * @property parentRace ID родительской расы (null = корневая раса)
 * @property subraces список ID подрас (пустой = нет подрас)
 * @property grants фиксированные гранты расы
 * @property choices выборы игрока при создании персонажа
 * @property modelSize переопределение масштаба модели для Pekhui (null = дефолт из CreatureSize)
 * @property tags теги для группировки и фильтрации
 */
data class RaceDefinition(
    override val id: Identifier,

    /**
     * ID родительской расы.
     * null — это корневая раса (не подраса).
     */
    val parentRace: Identifier? = null,

    /**
     * Список ID подрас.
     * Пустой — раса без подрас (Half-Orc, Tiefling).
     * Непустой — игрок обязан выбрать подрасу.
     */
    val subraces: List<Identifier> = emptyList(),

    /**
     * Фиксированные гранты — то что раса даёт всегда.
     */
    val grants: List<RaceGrant> = emptyList(),

    /**
     * Выборы игрока при создании персонажа.
     */
    val choices: List<RaceChoice> = emptyList(),

    /**
     * Переопределение масштаба модели для Pekhui API.
     *
     * null — использовать дефолтный масштаб из [CreatureSize.defaultModelScale].
     * Задаётся когда раса механически одного размера, но визуально другого.
     *
     * Пример: Гном — SMALL механически, но модель 0.75 вместо дефолтных 0.8.
     */
    val modelSize: Float? = null,

    /**
     * Теги для группировки и фильтрации.
     * Примеры: "humanoid", "fey", "undead", "construct"
     */
    val tags: List<String> = emptyList()
) : Definition, Registrable {

    // ── Удобные геттеры ───────────────────────────────────────────────────

    /** Является ли эта раса подрасой. */
    val isSubrace: Boolean get() = parentRace != null

    /** Имеет ли раса подрасы (игрок должен выбрать). */
    val hasSubraces: Boolean get() = subraces.isNotEmpty()

    /** Все гранты размера. */
    val sizeGrants: List<RaceGrant.Size>
        get() = grants.filterIsInstance<RaceGrant.Size>()

    /** Все гранты скорости. */
    val speedGrants: List<RaceGrant.Speed>
        get() = grants.filterIsInstance<RaceGrant.Speed>()

    /** Все гранты бонусов к статам. */
    val statBonusGrants: List<RaceGrant.StatBonus>
        get() = grants.filterIsInstance<RaceGrant.StatBonus>()

    /** Все гранты особенностей. */
    val featureGrants: List<RaceGrant.Feature>
        get() = grants.filterIsInstance<RaceGrant.Feature>()

    /** Все гранты языков. */
    val languageGrants: List<RaceGrant.Language>
        get() = grants.filterIsInstance<RaceGrant.Language>()

    /** Все гранты способностей. */
    val abilityGrants: List<RaceGrant.Ability>
        get() = grants.filterIsInstance<RaceGrant.Ability>()

    /**
     * Итоговый масштаб модели для Pekhui.
     * Использует [modelSize] если задан, иначе дефолт из первого гранта размера.
     */
    fun resolvedModelScale(): Float {
        if (modelSize != null) return modelSize
        return sizeGrants.firstOrNull()?.size?.defaultModelScale ?: CreatureSize.MEDIUM.defaultModelScale
    }

    /** Проверяет наличие тега. */
    fun hasTag(tag: String): Boolean = tag in tags

    override fun getTranslationKey(): String = "race.${id.namespace}.${id.path}"

    override fun validate() {
        // Подраса не должна иметь своих подрас
        if (isSubrace && hasSubraces) {
            throw IllegalStateException(
                "Race '$id' is a subrace (has parent_race) but also declares subraces. " +
                "Subraces cannot have their own subraces."
            )
        }
    }

    companion object {
        val CODEC: Codec<RaceDefinition> = RecordCodecBuilder.create { instance ->
            instance.group(
                CodecUtil.IDENTIFIER
                    .fieldOf("id")
                    .forGetter { it.id },
                CodecUtil.IDENTIFIER
                    .optionalFieldOf("parent_race")
                    .forGetter { java.util.Optional.ofNullable(it.parentRace) },
                CodecUtil.IDENTIFIER.listOf()
                    .optionalFieldOf("subraces", emptyList())
                    .forGetter { it.subraces },
                RaceGrant.CODEC.listOf()
                    .optionalFieldOf("grants", emptyList())
                    .forGetter { it.grants },
                RaceChoice.CODEC.listOf()
                    .optionalFieldOf("choices", emptyList())
                    .forGetter { it.choices },
                Codec.FLOAT
                    .optionalFieldOf("model_size")
                    .forGetter { java.util.Optional.ofNullable(it.modelSize) },
                Codec.STRING.listOf()
                    .optionalFieldOf("tags", emptyList())
                    .forGetter { it.tags }
            ).apply(instance) { id, parentRace, subraces, grants, choices, modelSize, tags ->
                RaceDefinition(
                    id = id,
                    parentRace = parentRace.orElse(null),
                    subraces = subraces,
                    grants = grants,
                    choices = choices,
                    modelSize = modelSize.orElse(null),
                    tags = tags
                )
            }
        }
    }
}
