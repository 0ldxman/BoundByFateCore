package omc.boundbyfate.api.relation

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * Участник отношения — организация или персонаж.
 *
 * Sealed class с двумя вариантами:
 * - [Organization] — идентифицируется по [Identifier]
 * - [Character] — идентифицируется по [UUID]
 *
 * Используется как часть [RelationKey] для хранения отношений в Map.
 */
sealed class RelationParty {

    /**
     * Организация как участник отношения.
     */
    data class Organization(val id: Identifier) : RelationParty() {
        override fun toString(): String = "org:$id"
    }

    /**
     * Персонаж как участник отношения.
     */
    data class Character(val uuid: UUID) : RelationParty() {
        override fun toString(): String = "char:$uuid"
    }

    companion object {
        private val UUID_CODEC: Codec<UUID> = Codec.STRING.xmap(
            { UUID.fromString(it) },
            { it.toString() }
        )

        val CODEC: Codec<RelationParty> = Codec.STRING.xmap(
            { str ->
                when {
                    str.startsWith("org:") -> Organization(Identifier(str.removePrefix("org:")))
                    str.startsWith("char:") -> Character(UUID.fromString(str.removePrefix("char:")))
                    else -> throw IllegalArgumentException("Unknown RelationParty format: $str")
                }
            },
            { party -> party.toString() }
        )
    }
}
