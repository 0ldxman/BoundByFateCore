package omc.boundbyfate.util.codec

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.serialization.Codec

/**
 * Утилиты для работы с JSON через Codec.
 */
object JsonUtil {
    
    /**
     * Codec для JsonObject.
     * Сериализует как строку JSON.
     */
    val JSON_OBJECT_CODEC: Codec<JsonObject> = Codec.STRING.xmap(
        { jsonString -> JsonParser.parseString(jsonString).asJsonObject },
        { jsonObject -> jsonObject.toString() }
    )
}
