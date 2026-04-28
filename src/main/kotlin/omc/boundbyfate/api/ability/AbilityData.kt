package omc.boundbyfate.api.ability

import com.google.gson.JsonObject
import net.minecraft.util.Identifier
import omc.boundbyfate.util.extension.toIdentifier

/**
 * Обёртка над [JsonObject] с удобными типизированными геттерами.
 *
 * Используется для чтения полей `data` и `scaling` из [AbilityDefinition].
 * Не требует схемы — каждая способность сама знает что там лежит.
 *
 * ## Использование
 *
 * ```kotlin
 * override fun execute(ctx: AbilityContext) {
 *     val dice   = ctx.data.getDiceExpression("damage_dice") // "8d6"
 *     val radius = ctx.data.getFloat("radius")               // 20.0
 *     val stat   = ctx.data.getId("save_stat")               // Identifier
 * }
 * ```
 */
@JvmInline
value class AbilityData(val json: JsonObject) {

    // ── Примитивы ─────────────────────────────────────────────────────────

    fun getInt(key: String, default: Int = 0): Int =
        json.get(key)?.asInt ?: default

    fun getFloat(key: String, default: Float = 0f): Float =
        json.get(key)?.asFloat ?: default

    fun getDouble(key: String, default: Double = 0.0): Double =
        json.get(key)?.asDouble ?: default

    fun getString(key: String, default: String = ""): String =
        json.get(key)?.asString ?: default

    fun getBool(key: String, default: Boolean = false): Boolean =
        json.get(key)?.asBoolean ?: default

    // ── Идентификаторы ────────────────────────────────────────────────────

    fun getId(key: String): Identifier? =
        json.get(key)?.asString?.toIdentifier()

    fun getIds(key: String): List<Identifier> =
        json.getAsJsonArray(key)
            ?.mapNotNull { it.asString?.toIdentifier() }
            ?: emptyList()

    // ── Кубики ────────────────────────────────────────────────────────────

    /**
     * Читает строку кубиков вида "8d6", "1d10", "2d4+2".
     * Возвращает null если поле отсутствует или некорректно.
     */
    fun getDiceExpression(key: String): DiceExpression? =
        json.get(key)?.asString?.let { DiceExpression.parse(it) }

    /**
     * Читает строку кубиков, выбрасывает исключение если поле отсутствует.
     */
    fun requireDiceExpression(key: String): DiceExpression =
        getDiceExpression(key)
            ?: error("Required dice expression '$key' not found in ability data")

    // ── Вложенные объекты ─────────────────────────────────────────────────

    fun getObject(key: String): AbilityData? =
        json.getAsJsonObject(key)?.let { AbilityData(it) }

    // ── Проверки ──────────────────────────────────────────────────────────

    fun has(key: String): Boolean = json.has(key)

    fun isEmpty(): Boolean = json.size() == 0

    companion object {
        val EMPTY = AbilityData(JsonObject())
    }
}
