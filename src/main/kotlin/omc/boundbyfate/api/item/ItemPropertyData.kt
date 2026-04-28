package omc.boundbyfate.api.item

import com.google.gson.JsonObject
import net.minecraft.util.Identifier
import omc.boundbyfate.util.extension.toIdentifier

/**
 * Обёртка над [JsonObject] с удобными типизированными геттерами.
 *
 * Аналог [omc.boundbyfate.api.effect.EffectData] для свойств предметов.
 * Используется для чтения параметров в [ItemPropertyHandler].
 *
 * ## Использование
 *
 * ```kotlin
 * object MeleeDamage : ItemPropertyHandler() {
 *     override fun onEquip(ctx: ItemPropertyContext) {
 *         val dice = ctx.data.getString("dice", "1d6")
 *         val stat = ctx.data.getId("stat")
 *     }
 * }
 * ```
 *
 * ## JSON
 *
 * ```json
 * {
 *   "id": "boundbyfate-core:melee_damage",
 *   "data": { "dice": "1d8", "stat": "boundbyfate-core:strength" }
 * }
 * ```
 */
@JvmInline
value class ItemPropertyData(val json: JsonObject) {

    fun getInt(key: String, default: Int = 0): Int =
        json.get(key)?.asInt ?: default

    fun requireInt(key: String): Int =
        json.get(key)?.asInt ?: error("Required int '$key' not found in item property data")

    fun getFloat(key: String, default: Float = 0f): Float =
        json.get(key)?.asFloat ?: default

    fun getString(key: String, default: String = ""): String =
        json.get(key)?.asString ?: default

    fun requireString(key: String): String =
        json.get(key)?.asString ?: error("Required string '$key' not found in item property data")

    fun getBool(key: String, default: Boolean = false): Boolean =
        json.get(key)?.asBoolean ?: default

    fun getId(key: String): Identifier? =
        json.get(key)?.asString?.toIdentifier()

    fun requireId(key: String): Identifier =
        getId(key) ?: error("Required identifier '$key' not found in item property data")

    fun getIds(key: String): List<Identifier> =
        json.getAsJsonArray(key)
            ?.mapNotNull { it.asString?.toIdentifier() }
            ?: emptyList()

    fun getStrings(key: String): List<String> =
        json.getAsJsonArray(key)
            ?.mapNotNull { it.asString }
            ?: emptyList()

    fun getObject(key: String): ItemPropertyData? =
        json.getAsJsonObject(key)?.let { ItemPropertyData(it) }

    fun has(key: String): Boolean = json.has(key)

    fun isEmpty(): Boolean = json.size() == 0

    companion object {
        val EMPTY = ItemPropertyData(JsonObject())
    }
}
