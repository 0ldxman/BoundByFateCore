package omc.boundbyfate.api.relation

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Отношение между двумя участниками.
 */
data class Relation(
    val value: Int = 0,
    val tags: Set<String> = emptySet(),
    val history: List<RelationEvent> = emptyList()
) {
    val status: RelationStatus get() = RelationStatus.fromValue(value)

    fun withValue(newValue: Int): Relation = copy(value = newValue)

    fun shift(delta: Int, description: String, timestamp: Long): Relation {
        val event = RelationEvent(description = description, valueDelta = delta, timestamp = timestamp)
        return copy(value = value + delta, history = history + event)
    }

    fun withTag(tag: String): Relation = copy(tags = tags + tag)
    fun withoutTag(tag: String): Relation = copy(tags = tags - tag)
    fun hasTag(tag: String): Boolean = tag in tags

    companion object {
        val CODEC: Codec<Relation> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("value").forGetter { it.value },
                Codec.STRING.listOf().xmap({ it.toSet() }, { it.toList() })
                    .fieldOf("tags").forGetter { it.tags },
                RelationEvent.CODEC.listOf().fieldOf("history").forGetter { it.history }
            ).apply(instance, ::Relation)
        }
    }
}

/**
 * Событие в истории отношений.
 */
data class RelationEvent(
    val description: String,
    val valueDelta: Int,
    val timestamp: Long
) {
    companion object {
        val CODEC: Codec<RelationEvent> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("description").forGetter { it.description },
                Codec.INT.fieldOf("valueDelta").forGetter { it.valueDelta },
                Codec.LONG.fieldOf("timestamp").forGetter { it.timestamp }
            ).apply(instance, ::RelationEvent)
        }
    }
}
