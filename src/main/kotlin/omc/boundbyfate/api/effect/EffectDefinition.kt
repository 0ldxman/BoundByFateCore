package omc.boundbyfate.api.effect

import com.google.gson.JsonObject
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.api.core.Definition
import omc.boundbyfate.util.codec.CodecUtil
import omc.boundbyfate.util.codec.JsonUtil

/**
 * Определение эффекта — данные из JSON датапака.
 *
 * Хранит только параметры. Вся логика живёт в [EffectHandler].
 *
 * ## Разделение ответственности
 *
 * ```
 * EffectDefinition (JSON)  — параметры эффекта
 * EffectHandler (Kotlin)   — логика apply/remove/tick
 * EffectContext            — контекст конкретного применения
 * ```
 *
 * ## Примеры JSON
 *
 * ### Простой эффект (штраф к атаке)
 * ```json
 * {
 *   "id": "boundbyfate-core:attack_penalty",
 *   "data": {
 *     "penalty": -4
 *   }
 * }
 * ```
 *
 * ### Длящийся эффект (яд)
 * ```json
 * {
 *   "id": "boundbyfate-core:poison",
 *   "data": {
 *     "damage_per_tick": 1,
 *     "tick_interval": 20
 *   }
 * }
 * ```
 *
 * ### Эффект с несколькими параметрами
 * ```json
 * {
 *   "id": "boundbyfate-core:stat_modifier",
 *   "data": {
 *     "stat": "boundbyfate-core:strength",
 *     "value": 2
 *   }
 * }
 * ```
 *
 * ## Использование в Feature
 *
 * ```json
 * {
 *   "grant_type": "effect",
 *   "id": "boundbyfate-core:darkvision",
 *   "data": { "range": 60 }
 * }
 * ```
 *
 * @property id уникальный идентификатор
 * @property data свободный блок параметров — каждый хендлер знает что здесь лежит
 */
data class EffectDefinition(
    override val id: Identifier,

    /**
     * Свободный блок параметров эффекта.
     * Читается через [EffectData] в коде хендлера.
     */
    val data: JsonObject = JsonObject()
) : Definition, omc.boundbyfate.api.core.Registrable {

    /**
     * Удобный доступ к data через EffectData.
     */
    val effectData: EffectData get() = EffectData(data)

    override fun getTranslationKey(): String =
        "effect.${id.namespace}.${id.path}"

    companion object {
        val CODEC: Codec<EffectDefinition> = RecordCodecBuilder.create { instance ->
            instance.group(
                CodecUtil.IDENTIFIER
                    .fieldOf("id")
                    .forGetter { it.id },
                JsonUtil.JSON_OBJECT_CODEC
                    .optionalFieldOf("data", JsonObject())
                    .forGetter { it.data }
            ).apply(instance, ::EffectDefinition)
        }
    }
}
