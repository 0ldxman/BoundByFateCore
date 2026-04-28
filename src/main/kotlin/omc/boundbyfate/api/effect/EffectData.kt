package omc.boundbyfate.api.effect

import com.google.gson.JsonObject
import net.minecraft.util.Identifier
import omc.boundbyfate.util.extension.toIdentifier

/**
 * Обёртка над [JsonObject] с удобными типизированными геттерами.
 *
 * Аналог [omc.boundbyfate.api.ability.AbilityData] для эффектов.
 * Используется для чтения параметров из JSON в [EffectHandler].
 *
 * Каждый хендлер сам знает что лежит в его data — никакой схемы не нужно.
 *
 * ## Использование
 *
 * ```kotlin
 * object AttackPenalty : EffectHandler() {
 *     override fun apply(ctx: EffectContext) {
 *         val penalty = ctx.data.getInt("penalty")
 *         val stat    = ctx.data.getId("stat")
 *     }
 * }
 * ```
 *
 * ## JSON
 *
 * ```json
 * {
 *   "grant_type": "effect",
 *   "id": "boundbyfate-core:attack_penalty",
 *   "data": {
 *     "penalty": -4,
 *     "stat": "boundbyfate-core:strength"
 *   }
 * }
 * ```
 */
@JvmInline
value class EffectData(val json: JsonObject) {

    // ── Примитивы ─────────────────────────────────────────────────────────

    fun getInt(key: String, default: Int = 0): Int =
        json.get(key)?.asInt ?: default

    fun requireInt(key: String): Int =
        json.get(key)?.asInt ?: error("Required int '$key' not found in effect data")

    fun getFloat(key: String, default: Float = 0f): Float =
        json.get(key)?.asFloat ?: default

    fun requireFloat(key: String): Float =
        json.get(key)?.asFloat ?: error("Required float '$key' not found in effect data")

    fun getDouble(key: String, default: Double = 0.0): Double =
        json.get(key)?.asDouble ?: default

    fun getString(key: String, default: String = ""): String =
        json.get(key)?.asString ?: default

    fun requireString(key: String): String =
        json.get(key)?.asString ?: error("Required string '$key' not found in effect data")

    fun getBool(key: String, default: Boolean = false): Boolean =
        json.get(key)?.asBoolean ?: default

    // ── Идентификаторы ────────────────────────────────────────────────────

    fun getId(key: String): Identifier? =
        json.get(key)?.asString?.toIdentifier()

    fun requireId(key: String): Identifier =
        getId(key) ?: error("Required identifier '$key' not found in effect data")

    fun getIds(key: String): List<Identifier> =
        json.getAsJsonArray(key)
            ?.mapNotNull { it.asString?.toIdentifier() }
            ?: emptyList()

    // ── Строки ────────────────────────────────────────────────────────────

    fun getStrings(key: String): List<String> =
        json.getAsJsonArray(key)
            ?.mapNotNull { it.asString }
            ?: emptyList()

    // ── Вложенные объекты ─────────────────────────────────────────────────

    fun getObject(key: String): EffectData? =
        json.getAsJsonObject(key)?.let { EffectData(it) }

    // ── Проверки ──────────────────────────────────────────────────────────

    fun has(key: String): Boolean = json.has(key)

    fun isEmpty(): Boolean = json.size() == 0

    companion object {
        val EMPTY = EffectData(JsonObject())
    }
}
