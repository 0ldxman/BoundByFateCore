package omc.boundbyfate.client.util

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import java.io.InputStream
import java.io.OutputStream

typealias ListOrSingle<T> = @Serializable(with = ListOrSingleSerializer::class) List<T>

class ListOrSingleSerializer<T>(
    elementSerializer: KSerializer<T>,
) : JsonTransformingSerializer<List<T>>(ListSerializer(elementSerializer)) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonArray) element
        else JsonArray(listOf(element))

    override fun transformSerialize(element: JsonElement): JsonElement =
        (element as? JsonArray)?.singleOrNull() ?: element
}

open class SnakeAsUpperCaseSerializer<T : Any>(val inner: KSerializer<T>) : JsonTransformingSerializer<T>(inner) {
    override fun transformSerialize(element: JsonElement): JsonElement =
        if (element is JsonPrimitive && element.isString) {
            JsonPrimitive(element.content.uppercase())
        } else {
            element
        }

    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonPrimitive && element.isString) {
            JsonPrimitive(element.content.lowercase())
        } else {
            element
        }
}

@OptIn(ExperimentalSerializationApi::class)
val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
    allowSpecialFloatingPointValues = true
    useArrayPolymorphism = true
    prettyPrint = true
    prettyPrintIndent = "  "
    allowComments = true
    allowTrailingComma = true
}

@OptIn(ExperimentalSerializationApi::class)
object JsonFormat {
    val serializersModule: SerializersModule = json.serializersModule

    fun <V> serialize(serializer: SerializationStrategy<V>, value: V): JsonElement =
        json.encodeToJsonElement(serializer, value)

    fun <V> deserialize(deserializer: DeserializationStrategy<V>, data: JsonElement): V =
        json.decodeFromJsonElement(deserializer, data)

    inline fun <reified T> decodeFromString(string: String): T = json.decodeFromString(string)

    fun encodeToString(element: JsonElement): String = json.encodeToString(JsonElement.serializer(), element)

    inline fun <reified T> decodeFromStream(stream: InputStream): T = json.decodeFromStream(stream)

    fun encodeToStream(element: JsonElement, stream: OutputStream) {
        stream.writer().use { it.write(encodeToString(element)) }
    }
}

