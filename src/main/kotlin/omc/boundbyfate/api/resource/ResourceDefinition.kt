package omc.boundbyfate.api.resource

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.api.core.Definition
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Определение ресурса (Resource).
 *
 * Ресурс — это именованный счётчик с правилами восстановления.
 * Загружается из JSON датапаков, хранится в Registry.
 *
 * ## Что такое ресурс
 *
 * Ресурс описывает *тип* ячеек или очков, но не их количество у конкретного персонажа.
 * Количество задаётся в [omc.boundbyfate.api.level.LevelGrant] при повышении уровня.
 *
 * Примеры ресурсов:
 * - Ячейки заклинаний 1-9 уровня
 * - Очки ярости (Barbarian)
 * - Очки ки (Monk)
 * - Очки чародейства (Sorcerer)
 * - Кости превосходства (Battle Master)
 * - Второе дыхание (Fighter)
 * - Дикий облик (Druid)
 *
 * ## Разделение ответственности
 *
 * - [ResourceDefinition] — *что* это за ресурс и когда восстанавливается (Registry)
 * - `LevelGrant.resources` — *сколько* ресурса даётся на уровне
 * - `EntityResourceData` — *текущее* количество у персонажа (Attachment)
 *
 * ## Пример JSON
 *
 * ```json
 * {
 *   "id": "boundbyfate-core:ki_points",
 *   "recovery": {"type": "on_event", "event": "boundbyfate-core:rest/short"}
 * }
 * ```
 *
 * ```json
 * {
 *   "id": "boundbyfate-core:spell_slot_1",
 *   "recovery": {"type": "on_event", "event": "boundbyfate-core:rest/long"}
 * }
 * ```
 *
 * @property id уникальный идентификатор ресурса
 * @property recovery правило восстановления
 */
data class ResourceDefinition(
    override val id: Identifier,
    val recovery: ResourceRecovery
) : Definition {

    override fun getTranslationKey(): String = "resource.${id.namespace}.${id.path}"

    companion object {
        val CODEC: Codec<ResourceDefinition> = RecordCodecBuilder.create { instance ->
            instance.group(
                CodecUtil.IDENTIFIER.fieldOf("id").forGetter { it.id },
                ResourceRecovery.CODEC.fieldOf("recovery").forGetter { it.recovery }
            ).apply(instance, ::ResourceDefinition)
        }
    }
}
