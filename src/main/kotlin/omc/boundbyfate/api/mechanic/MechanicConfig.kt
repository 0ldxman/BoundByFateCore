package omc.boundbyfate.api.mechanic

import com.google.gson.JsonObject
import net.minecraft.util.Identifier

/**
 * Обёртка над JsonObject для удобного доступа к конфигурации механики.
 *
 * Предоставляет типобезопасные методы для чтения параметров.
 * Аналогично [omc.boundbyfate.api.ability.AbilityData].
 *
 * ## Использование
 *
 * ```kotlin
 * override fun onActivate(player: ServerPlayerEntity, config: MechanicConfig) {
 *     val stat = config.getString("stat") ?: "intelligence"
 *     val type = config.getString("type") ?: "full"
 *     val ritualCasting = config.getBoolean("ritual_casting") ?: false
 *     val startingSpells = config.getInt("starting_spells") ?: 6
 * }
 * ```
 */
class MechanicConfig(private val json: JsonObject) {
    
    /**
     * Получает строковое значение.
     */
    fun getString(key: String): String? {
        return if (json.has(key)) json.get(key).asString else null
    }
    
    /**
     * Получает строковое значение или дефолт.
     */
    fun getString(key: String, default: String): String {
        return getString(key) ?: default
    }
    
    /**
     * Получает целочисленное значение.
     */
    fun getInt(key: String): Int? {
        return if (json.has(key)) json.get(key).asInt else null
    }
    
    /**
     * Получает целочисленное значение или дефолт.
     */
    fun getInt(key: String, default: Int): Int {
        return getInt(key) ?: default
    }
    
    /**
     * Получает булево значение.
     */
    fun getBoolean(key: String): Boolean? {
        return if (json.has(key)) json.get(key).asBoolean else null
    }
    
    /**
     * Получает булево значение или дефолт.
     */
    fun getBoolean(key: String, default: Boolean): Boolean {
        return getBoolean(key) ?: default
    }
    
    /**
     * Получает double значение.
     */
    fun getDouble(key: String): Double? {
        return if (json.has(key)) json.get(key).asDouble else null
    }
    
    /**
     * Получает double значение или дефолт.
     */
    fun getDouble(key: String, default: Double): Double {
        return getDouble(key) ?: default
    }
    
    /**
     * Получает Identifier.
     */
    fun getIdentifier(key: String): Identifier? {
        val str = getString(key) ?: return null
        return try {
            Identifier.of(str)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Получает Identifier или дефолт.
     */
    fun getIdentifier(key: String, default: Identifier): Identifier {
        return getIdentifier(key) ?: default
    }
    
    /**
     * Получает список строк.
     */
    fun getStringList(key: String): List<String>? {
        if (!json.has(key)) return null
        val array = json.getAsJsonArray(key)
        return array.map { it.asString }
    }
    
    /**
     * Получает список строк или дефолт.
     */
    fun getStringList(key: String, default: List<String>): List<String> {
        return getStringList(key) ?: default
    }
    
    /**
     * Получает список Identifier.
     */
    fun getIdentifierList(key: String): List<Identifier>? {
        val strings = getStringList(key) ?: return null
        return strings.mapNotNull { 
            try {
                Identifier.of(it)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Получает список Identifier или дефолт.
     */
    fun getIdentifierList(key: String, default: List<Identifier>): List<Identifier> {
        return getIdentifierList(key) ?: default
    }
    
    /**
     * Проверяет наличие ключа.
     */
    fun has(key: String): Boolean = json.has(key)
    
    /**
     * Получает вложенный JsonObject.
     */
    fun getObject(key: String): JsonObject? {
        return if (json.has(key)) json.getAsJsonObject(key) else null
    }
    
    /**
     * Получает вложенную конфигурацию.
     */
    fun getNested(key: String): MechanicConfig? {
        val obj = getObject(key) ?: return null
        return MechanicConfig(obj)
    }
    
    /**
     * Возвращает сырой JsonObject.
     */
    fun raw(): JsonObject = json
    
    /**
     * Объединяет с другой конфигурацией (override).
     * Значения из other перезаписывают значения из this.
     */
    fun merge(other: MechanicConfig): MechanicConfig {
        val merged = JsonObject()
        
        // Копируем все из this
        for (entry in json.entrySet()) {
            merged.add(entry.key, entry.value)
        }
        
        // Перезаписываем значениями из other
        for (entry in other.json.entrySet()) {
            merged.add(entry.key, entry.value)
        }
        
        return MechanicConfig(merged)
    }
    
    companion object {
        /**
         * Пустая конфигурация.
         */
        val EMPTY = MechanicConfig(JsonObject())
    }
}
