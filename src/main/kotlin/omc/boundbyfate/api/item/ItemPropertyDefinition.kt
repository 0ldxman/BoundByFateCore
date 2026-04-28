package omc.boundbyfate.api.item

import com.google.gson.JsonObject
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.util.codec.CodecUtil
import omc.boundbyfate.util.codec.JsonUtil

/**
 * Определение свойства предмета — данные из JSON.
 *
 * Аналог [omc.boundbyfate.api.effect.EffectDefinition].
 * Хранит только id и параметры. Логика живёт в [ItemPropertyHandler].
 *
 * ## Примеры JSON
 *
 * ```json
 * {"id": "boundbyfate-core:finesse"}
 * {"id": "boundbyfate-core:melee_damage", "data": {"dice": "1d8", "stat": "boundbyfate-core:strength"}}
 * {"id": "boundbyfate-core:versatile",    "data": {"one_hand": "1d8", "two_hand": "1d10"}}
 * {"id": "boundbyfate-core:thrown",       "data": {"normal": 20, "long": 60}}
 * {"id": "boundbyfate-core:grants_ability","data": {"ability": "boundbyfate-core:fireball", "uses": 1, "recovery": "long_rest"}}
 * {"id": "boundbyfate-core:stat_bonus",   "data": {"stat": "boundbyfate-core:charisma", "value": 2}}
 * ```
 */
data class ItemPropertyDefinition(
    val id: Identifier,
    val data: JsonObject = JsonObject()
) {
    val propertyData: ItemPropertyData get() = ItemPropertyData(data)

    companion object {
        val CODEC: Codec<ItemPropertyDefinition> = RecordCodecBuilder.create { instance ->
            instance.group(
                CodecUtil.IDENTIFIER
                    .fieldOf("id")
                    .forGetter { it.id },
                JsonUtil.JSON_OBJECT_CODEC
                    .optionalFieldOf("data", JsonObject())
                    .forGetter { it.data }
            ).apply(instance, ::ItemPropertyDefinition)
        }
    }
}
