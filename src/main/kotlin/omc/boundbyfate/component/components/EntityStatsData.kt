package omc.boundbyfate.component.components

import com.mojang.serialization.Codec
import net.minecraft.util.Identifier
import omc.boundbyfate.api.skill.ProficiencyLevel
import omc.boundbyfate.api.stat.StatModifier
import omc.boundbyfate.api.stat.StatValue
import omc.boundbyfate.component.core.BbfComponent
import omc.boundbyfate.component.core.BbfComponents
import omc.boundbyfate.component.core.SyncMode
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Вычисленные характеристики, навыки, владения и механики персонажа.
 *
 * ## Что хранит
 *
 * - `calculatedStats` — итоговые значения статов с модификаторами
 * - `permanentModifiers` — от расы/класса/фитов (пересоздаются при загрузке)
 * - `temporaryModifiers` — от заклинаний/зелий/предметов (runtime)
 * - `savingThrowProficiencies` — владения спасбросками
 * - `skills` — вычисленные навыки
 * - `itemProficiencies` — владения предметами/инструментами
 * - `mechanics` — активные механики класса
 *
 * ## Жизненный цикл
 *
 * При загрузке персонажа:
 * 1. `permanentModifiers` пересоздаются из Registries (раса, класс, фиты)
 * 2. `temporaryModifiers` восстанавливаются из componentsSnapshot
 * 3. `calculatedStats` пересчитываются
 *
 * При изменении модификаторов — `calculatedStats` и `skills` пересчитываются.
 */
class EntityStatsData : BbfComponent() {

    // ── Статы ─────────────────────────────────────────────────────────────

    /**
     * Итоговые значения характеристик с учётом всех модификаторов.
     * Ключ — ID стата (например "boundbyfate-core:strength").
     */
    val calculatedStats by syncedMap<Identifier, StatValue>(
        Identifier.CODEC,
        StatValue.CODEC
    )

    /**
     * Постоянные модификаторы от расы/класса/фитов/ASI.
     * Пересоздаются при каждой загрузке персонажа из Registries.
     * Ключ — ID стата.
     */
    val permanentModifiers by syncedMap(
        Identifier.CODEC,
        StatModifier.CODEC.listOf()
    )

    /**
     * Временные модификаторы от заклинаний/зелий/предметов/эффектов.
     * Добавляются/удаляются в runtime.
     * Ключ — ID стата.
     */
    val temporaryModifiers by syncedMap(
        Identifier.CODEC,
        StatModifier.CODEC.listOf()
    )

    // ── Спасброски ────────────────────────────────────────────────────────

    /**
     * Уровни владения спасбросками.
     * Ключ — ID стата (например "boundbyfate-core:strength").
     */
    val savingThrowProficiencies by syncedMap(
        Identifier.CODEC,
        PROFICIENCY_LEVEL_CODEC
    )

    // ── Навыки ────────────────────────────────────────────────────────────

    /**
     * Вычисленные навыки с уровнями владения.
     * Ключ — ID навыка (например "boundbyfate-core:athletics").
     */
    val skills by syncedMap(
        Identifier.CODEC,
        SKILL_VALUE_CODEC
    )

    // ── Владения предметами ───────────────────────────────────────────────

    /**
     * Владения предметами, инструментами, языками.
     * Заполняется при загрузке из CharacterData.stats.proficiencies.
     */
    val itemProficiencies by syncedList(Identifier.CODEC)

    // ── Механики ──────────────────────────────────────────────────────────

    /**
     * Активные механики класса (Spellcasting, Rage, Ki и т.д.).
     * Заполняется ClassMechanicManager при активации механик.
     */
    val mechanics by syncedList(Identifier.CODEC)

    // ── Удобные методы ────────────────────────────────────────────────────

    /** Возвращает итоговое значение стата или null. */
    fun getStat(statId: Identifier): StatValue? = calculatedStats[statId]

    /** Возвращает модификатор стата (для бросков). */
    fun getStatModifier(statId: Identifier): Int = calculatedStats[statId]?.modifier ?: 0

    /** Проверяет наличие владения предметом. */
    fun hasProficiency(proficiencyId: Identifier): Boolean = proficiencyId in itemProficiencies

    /** Проверяет наличие механики. */
    fun hasMechanic(mechanicId: Identifier): Boolean = mechanicId in mechanics

    companion object {
        val TYPE = BbfComponents.register(
            id = "boundbyfate-core:stats",
            syncMode = SyncMode.ON_CHANGE,
            factory = ::EntityStatsData
        )

        private val PROFICIENCY_LEVEL_CODEC: Codec<ProficiencyLevel> = Codec.STRING.xmap(
            { ProficiencyLevel.valueOf(it.uppercase()) },
            { it.name.lowercase() }
        )

        /** Codec для вычисленного навыка: итоговый бонус + уровень владения. */
        private val SKILL_VALUE_CODEC: Codec<SkillValue> = com.mojang.serialization.codecs.RecordCodecBuilder.create { i ->
            i.group(
                Codec.INT.fieldOf("bonus").forGetter { it.bonus },
                PROFICIENCY_LEVEL_CODEC.fieldOf("proficiency").forGetter { it.proficiency }
            ).apply(i, ::SkillValue)
        }
    }
}

/**
 * Вычисленное значение навыка.
 *
 * @property bonus итоговый бонус к броску (stat modifier + proficiency bonus * multiplier)
 * @property proficiency уровень владения
 */
data class SkillValue(
    val bonus: Int,
    val proficiency: ProficiencyLevel
) {
    companion object {
        val CODEC: Codec<SkillValue> = com.mojang.serialization.codecs.RecordCodecBuilder.create { i ->
            i.group(
                Codec.INT.fieldOf("bonus").forGetter { it.bonus },
                Codec.STRING.xmap(
                    { ProficiencyLevel.valueOf(it.uppercase()) },
                    { it.name.lowercase() }
                ).fieldOf("proficiency").forGetter { it.proficiency }
            ).apply(i, ::SkillValue)
        }
    }
}
