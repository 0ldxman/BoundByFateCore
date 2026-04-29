package omc.boundbyfate.client.gui.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Персистентное состояние UI — сохраняется между сессиями.
 *
 * Используется для запоминания открытых вкладок, позиций панелей,
 * пользовательских настроек интерфейса.
 *
 * ## Использование
 *
 * ```kotlin
 * class GmDashboard : BbfScreen() {
 *     private var activeTab by UiState.int("gm.dashboard.tab", default = 0)
 *     private var panelWidth by UiState.int("gm.dashboard.panel_width", default = 200)
 *
 *     override fun onClose() {
 *         UiState.save()
 *         super.onClose()
 *     }
 * }
 * ```
 */
object UiState {
    private val logger = LoggerFactory.getLogger(UiState::class.java)
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val data = mutableMapOf<String, Any>()
    private val file: File by lazy {
        FabricLoader.getInstance().configDir.resolve("boundbyfate_ui_state.json").toFile()
    }

    // ── Геттеры/сеттеры ───────────────────────────────────────────────────

    fun getString(key: String, default: String = ""): String =
        data.getOrDefault(key, default) as? String ?: default

    fun getInt(key: String, default: Int = 0): Int =
        (data.getOrDefault(key, default) as? Number)?.toInt() ?: default

    fun getFloat(key: String, default: Float = 0f): Float =
        (data.getOrDefault(key, default) as? Number)?.toFloat() ?: default

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        data.getOrDefault(key, default) as? Boolean ?: default

    fun set(key: String, value: Any) { data[key] = value }

    // ── Делегаты ──────────────────────────────────────────────────────────

    fun string(key: String, default: String = "") = UiStateDelegate(
        getter = { getString(key, default) },
        setter = { set(key, it) }
    )

    fun int(key: String, default: Int = 0) = UiStateDelegate(
        getter = { getInt(key, default) },
        setter = { set(key, it) }
    )

    fun float(key: String, default: Float = 0f) = UiStateDelegate(
        getter = { getFloat(key, default) },
        setter = { set(key, it) }
    )

    fun boolean(key: String, default: Boolean = false) = UiStateDelegate(
        getter = { getBoolean(key, default) },
        setter = { set(key, it) }
    )

    // ── Сохранение/загрузка ───────────────────────────────────────────────

    fun save() {
        try {
            val json = JsonObject()
            data.forEach { (key, value) ->
                when (value) {
                    is String  -> json.addProperty(key, value)
                    is Int     -> json.addProperty(key, value)
                    is Float   -> json.addProperty(key, value)
                    is Boolean -> json.addProperty(key, value)
                }
            }
            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(json))
        } catch (e: Exception) {
            logger.warn("Failed to save UI state", e)
        }
    }

    fun load() {
        if (!file.exists()) return
        try {
            val json = JsonParser.parseString(file.readText()).asJsonObject
            json.entrySet().forEach { (key, element) ->
                data[key] = when {
                    element.isJsonPrimitive -> {
                        val prim = element.asJsonPrimitive
                        when {
                            prim.isBoolean -> prim.asBoolean
                            prim.isNumber  -> prim.asFloat
                            else           -> prim.asString
                        }
                    }
                    else -> element.toString()
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to load UI state", e)
        }
    }
}

class UiStateDelegate<T>(
    private val getter: () -> T,
    private val setter: (T) -> Unit
) {
    operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = getter()
    operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) = setter(value)
}
