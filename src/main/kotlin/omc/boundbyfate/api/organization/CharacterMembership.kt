package omc.boundbyfate.api.organization

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier

/**
 * Членство персонажа в организации.
 */
data class CharacterMembership(
    val organizationId: Identifier,
    val ranks: Set<String> = emptySet(),
    val joinedAt: Long = 0L
) {
    fun hasRank(rankId: String): Boolean = rankId in ranks
    fun withRank(rankId: String): CharacterMembership = copy(ranks = ranks + rankId)
    fun withoutRank(rankId: String): CharacterMembership = copy(ranks = ranks - rankId)
    fun withRanks(newRanks: Set<String>): CharacterMembership = copy(ranks = newRanks)

    companion object {
        val CODEC: Codec<CharacterMembership> = RecordCodecBuilder.create { instance ->
            instance.group(
                Identifier.CODEC.fieldOf("organizationId").forGetter { it.organizationId },
                Codec.STRING.listOf().xmap({ it.toSet() }, { it.toList() })
                    .fieldOf("ranks").forGetter { it.ranks },
                Codec.LONG.fieldOf("joinedAt").forGetter { it.joinedAt }
            ).apply(instance, ::CharacterMembership)
        }
    }
}
