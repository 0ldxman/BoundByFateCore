package omc.boundbyfate.api.status

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.api.core.Definition
import omc.boundbyfate.api.effect.EffectDefinition
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Определение состояния (Status Condition).
 *
 * Состояние — это временный набор эффектов, применяемых к сущности.
 * Загружается из JSON датапаков, хранится в Registry.
 *
 * ## Архитектура
 *
 * ```
 * StatusDefinition (Registry)     — правила: что делает состояние
 *         ↓
 * ActiveStatus (EntityStatusData) — факт: состояние активно на сущности
 * ```
 *
 * ## Иерархия через includes
 *
 * Состояния могут включать другие состояния.
 * Например, "Ошеломлённый" включает "Недееспособный" — при применении
 * ошеломления система автоматически применяет и недееспособность.
 *
 * ```json
 * {
 *   "id": "boundbyfate-core:stunned",
 *   "includes": ["boundbyfate-core:incapacitated"],
 *   "effects": [...]
 * }
 * ```
 *
 * ## Примеры JSON
 *
 * ### Простое состояние
 * ```json
 * {
 *   "id": "boundbyfate-core:blinded",
 *   "effects": [
 *     {"type": "boundbyfate-core:advantage", "on": "attack_against", "advantage": "advantage"},
 *     {"type": "boundbyfate-core:advantage", "on": "attack_roll", "advantage": "disadvantage"}
 *   ]
 * }
 * ```
 *
 * ### Состояние с иерархией
 * ```json
 * {
 *   "id": "boundbyfate-core:paralyzed",
 *   "includes": ["boundbyfate-core:incapacitated"],
 *   "effects": [
 *     {"type": "boundbyfate-core:auto_fail_save", "stat": "boundbyfate-core:strength"},
 *     {"type": "boundbyfate-core:auto_fail_save", "stat": "boundbyfate-core:dexterity"},
 *     {"type": "boundbyfate-core:advantage", "on": "attack_against", "advantage": "advantage"},
 *     {"type": "boundbyfate-core:melee_crit_auto"}
 *   ]
 * }
 * ```
 *
 * @property id уникальный идентификатор
 * @property includes состояния, которые это состояние включает в себя
 * @property effects эффекты, применяемые при активации состояния
 */
data class StatusDefinition(
    override val id: Identifier,

    /**
     * Состояния, которые включает это состояние.
     *
     * При применении этого состояния все включённые состояния
     * также применяются автоматически.
     * При снятии — снимаются тоже.
     *
     * Примеры:
     * - Парализованный включает Недееспособного
     * - Ошеломлённый включает Недееспособного
     * - Бессознательный включает Недееспособного
     */
    val includes: List<Identifier> = emptyList(),

    /**
     * Эффекты, применяемые при активации состояния.
     *
     * Используют ту же систему эффектов что и Features и Abilities.
     * Снимаются автоматически при деактивации состояния.
     */
    val effects: List<EffectDefinition> = emptyList()
) : Definition {

    override fun getTranslationKey(): String = "status.${id.namespace}.${id.path}"

    companion object {
        val CODEC: Codec<StatusDefinition> = RecordCodecBuilder.create { instance ->
            instance.group(
                CodecUtil.IDENTIFIER.fieldOf("id").forGetter { it.id },
                CodecUtil.IDENTIFIER.listOf()
                    .optionalFieldOf("includes", emptyList())
                    .forGetter { it.includes },
                EffectDefinition.CODEC.listOf()
                    .optionalFieldOf("effects", emptyList())
                    .forGetter { it.effects }
            ).apply(instance, ::StatusDefinition)
        }
    }
}
