package omc.boundbyfate.api.ability

import com.google.gson.JsonObject
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.api.action.ActionSlotType
import omc.boundbyfate.api.core.Definition
import omc.boundbyfate.api.resource.ResourceRecovery
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Определение способности — данные из JSON датапака.
 *
 * Хранит только то что балансируется без перекомпиляции:
 * числа, кубики, идентификаторы, флаги.
 * Вся логика живёт в [AbilityHandler].
 *
 * ## Разделение ответственности
 *
 * ```
 * AbilityDefinition (JSON)    — что это за способность и её параметры
 * AbilityHandler (Kotlin)     — как она работает
 * AbilityContext              — контекст конкретного использования
 * ```
 *
 * ## Пример JSON
 *
 * ```json
 * {
 *   "id": "boundbyfate-core:fireball",
 *   "action_cost": "action",
 *   "spell_data": {
 *     "school": "boundbyfate-core:evocation",
 *     "available_for_classes": ["boundbyfate-core:wizard"],
 *     "is_concentration": false,
 *     "is_ritual": false
 *   },
 *   "data": {
 *     "damage_dice": "8d6",
 *     "radius": 20,
 *     "save_stat": "boundbyfate-core:dexterity"
 *   },
 *   "scaling": {
 *     "damage_dice_count": 1
 *   }
 * }
 * ```
 *
 * @property id уникальный идентификатор способности
 * @property actionCost тип действия для использования
 * @property recovery восстановление (null = нет ограничений на использование)
 * @property spellData данные заклинания (null = не заклинание)
 * @property tags теги для группировки и фильтрации
 * @property data свободный блок параметров баланса
 * @property scaling свободный блок параметров масштабирования
 */
data class AbilityDefinition(
    override val id: Identifier,

    /**
     * Тип действия для использования способности.
     * По умолчанию — основное действие.
     */
    val actionCost: ActionSlotType = ActionSlotType.ACTION,

    /**
     * Правило восстановления.
     *
     * null — способность без ограничений (можно использовать всегда).
     * Задаётся для способностей типа "раз в короткий/длинный отдых"
     * которые не тратят специфичные ресурсы (Ki, Rage и т.д.).
     *
     * Для способностей с ресурсами — ресурс управляет восстановлением сам.
     */
    val recovery: ResourceRecovery? = null,

    /**
     * Данные заклинания.
     * null — способность не является заклинанием.
     * Если задано — тег "spell" добавляется автоматически.
     */
    val spellData: SpellData? = null,

    /**
     * Теги для группировки и фильтрации.
     *
     * Примеры: "aoe", "projectile", "melee", "healing", "uncounterable"
     *
     * Тег "spell" добавляется автоматически если задан [spellData].
     */
    val tags: List<String> = emptyList(),

    /**
     * Свободный блок параметров баланса.
     *
     * Каждая способность сама знает что здесь лежит.
     * Читается через [AbilityData] в коде хендлера.
     *
     * Примеры полей: "damage_dice", "radius", "range", "save_stat"
     */
    val data: JsonObject = JsonObject(),

    /**
     * Свободный блок параметров масштабирования.
     *
     * Что именно масштабируется и от чего — решает код способности.
     * Система не накладывает никакой семантики на этот блок.
     *
     * Примеры полей: "damage_dice_count", "damage_per_ki", "radius_per_level"
     */
    val scaling: JsonObject = JsonObject()
) : Definition, omc.boundbyfate.api.core.Registrable {

    /**
     * Эффективные теги — включают автоматически добавленные.
     * Тег "spell" добавляется если задан [spellData].
     */
    val effectiveTags: List<String> get() = buildList {
        addAll(tags)
        if (spellData != null && "spell" !in tags) add("spell")
    }

    /**
     * Проверяет наличие тега.
     */
    fun hasTag(tag: String): Boolean = tag in effectiveTags

    /**
     * Является ли способность заклинанием.
     */
    val isSpell: Boolean get() = spellData != null

    /**
     * Удобный доступ к data через AbilityData.
     */
    val abilityData: AbilityData get() = AbilityData(data)

    /**
     * Удобный доступ к scaling через AbilityData.
     */
    val abilityScaling: AbilityData get() = AbilityData(scaling)

    override fun getTranslationKey(): String =
        "ability.${id.namespace}.${id.path}"

    companion object {
        // Codec для JsonObject — сериализуем как строку JSON
        private val JSON_OBJECT_CODEC: Codec<JsonObject> = Codec.STRING.xmap(
            { com.google.gson.JsonParser.parseString(it).asJsonObject },
            { it.toString() }
        )

        val CODEC: Codec<AbilityDefinition> = RecordCodecBuilder.create { instance ->
            instance.group(
                CodecUtil.IDENTIFIER
                    .fieldOf("id")
                    .forGetter { it.id },
                ActionSlotType.CODEC
                    .optionalFieldOf("action_cost", ActionSlotType.ACTION)
                    .forGetter { it.actionCost },
                ResourceRecovery.CODEC
                    .optionalFieldOf("recovery")
                    .forGetter { java.util.Optional.ofNullable(it.recovery) },
                SpellData.CODEC
                    .optionalFieldOf("spell_data")
                    .forGetter { java.util.Optional.ofNullable(it.spellData) },
                Codec.STRING.listOf()
                    .optionalFieldOf("tags", emptyList())
                    .forGetter { it.tags },
                JSON_OBJECT_CODEC
                    .optionalFieldOf("data", JsonObject())
                    .forGetter { it.data },
                JSON_OBJECT_CODEC
                    .optionalFieldOf("scaling", JsonObject())
                    .forGetter { it.scaling }
            ).apply(instance) { id, actionCost, recovery, spellData, tags, data, scaling ->
                AbilityDefinition(
                    id = id,
                    actionCost = actionCost,
                    recovery = recovery.orElse(null),
                    spellData = spellData.orElse(null),
                    tags = tags,
                    data = data,
                    scaling = scaling
                )
            }
        }
    }
}

/**
 * Данные заклинания — опциональный блок только для заклинаний.
 *
 * @property school школа магии
 * @property availableForClasses классы которые могут изучить заклинание
 * @property availableForSubclasses подклассы которые могут изучить заклинание
 * @property isConcentration требует ли концентрации
 * @property isRitual можно ли кастовать как ритуал
 */
data class SpellData(
    val school: Identifier,
    val availableForClasses: List<Identifier> = emptyList(),
    val availableForSubclasses: List<Identifier> = emptyList(),
    val isConcentration: Boolean = false,
    val isRitual: Boolean = false
) {
    companion object {
        val CODEC: Codec<SpellData> = RecordCodecBuilder.create { instance ->
            instance.group(
                CodecUtil.IDENTIFIER
                    .fieldOf("school")
                    .forGetter { it.school },
                CodecUtil.IDENTIFIER.listOf()
                    .optionalFieldOf("available_for_classes", emptyList())
                    .forGetter { it.availableForClasses },
                CodecUtil.IDENTIFIER.listOf()
                    .optionalFieldOf("available_for_subclasses", emptyList())
                    .forGetter { it.availableForSubclasses },
                Codec.BOOL
                    .optionalFieldOf("is_concentration", false)
                    .forGetter { it.isConcentration },
                Codec.BOOL
                    .optionalFieldOf("is_ritual", false)
                    .forGetter { it.isRitual }
            ).apply(instance, ::SpellData)
        }
    }
}
