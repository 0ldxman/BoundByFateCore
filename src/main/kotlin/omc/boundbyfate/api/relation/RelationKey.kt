package omc.boundbyfate.api.relation

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Ключ отношения — направленная пара участников.
 *
 * Отношение направленное: `(A → B)` и `(B → A)` — разные отношения.
 * Например, Гильдия Воров ненавидит Стражу, но Стража может не знать об этом.
 *
 * Используется как ключ в `Map<RelationKey, Relation>`.
 * `data class` гарантирует корректные `equals` и `hashCode`.
 */
data class RelationKey(
    val from: RelationParty,
    val to: RelationParty
) {
    override fun toString(): String = "$from → $to"

    companion object {
        /**
         * Codec для использования как ключ в Map (сериализуется как строка "from|to").
         */
        val CODEC: Codec<RelationKey> = Codec.STRING.xmap(
            { str ->
                val parts = str.split("|", limit = 2)
                require(parts.size == 2) { "Invalid RelationKey format: $str" }
                RelationKey(
                    from = RelationParty.CODEC.parse(
                        com.mojang.serialization.JsonOps.INSTANCE,
                        com.google.gson.JsonPrimitive(parts[0])
                    ).result().orElseThrow { IllegalArgumentException("Bad from: ${parts[0]}") },
                    to = RelationParty.CODEC.parse(
                        com.mojang.serialization.JsonOps.INSTANCE,
                        com.google.gson.JsonPrimitive(parts[1])
                    ).result().orElseThrow { IllegalArgumentException("Bad to: ${parts[1]}") }
                )
            },
            { key -> "${key.from}|${key.to}" }
        )
    }
}
