package omc.boundbyfate.api.organization

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier

/**
 * Организация — гильдия, орден, фракция, торговая компания и т.д.
 */
data class Organization(
    val id: Identifier,
    val name: String,
    val description: String = "",
    val logo: Identifier? = null,
    val ranks: List<OrgRank> = emptyList(),
    val rankThresholds: List<RankThreshold> = emptyList()
) {
    fun getRank(rankId: String): OrgRank? = ranks.firstOrNull { it.id == rankId }
    fun ranksSorted(): List<OrgRank> = ranks.sortedBy { it.level }
    fun getAutoRanksForReputation(reputation: Int): Set<String> =
        rankThresholds.filter { it.reputation <= reputation }.map { it.rankId }.toSet()
    fun hasRank(rankId: String): Boolean = ranks.any { it.id == rankId }

    companion object {
        val CODEC: Codec<Organization> = RecordCodecBuilder.create { instance ->
            instance.group(
                Identifier.CODEC.fieldOf("id").forGetter { it.id },
                Codec.STRING.fieldOf("name").forGetter { it.name },
                Codec.STRING.optionalFieldOf("description", "").forGetter { it.description },
                Identifier.CODEC.optionalFieldOf("logo").forGetter {
                    java.util.Optional.ofNullable(it.logo)
                },
                OrgRank.CODEC.listOf().fieldOf("ranks").forGetter { it.ranks },
                RankThreshold.CODEC.listOf().fieldOf("rankThresholds").forGetter { it.rankThresholds }
            ).apply(instance) { id, name, desc, logo, ranks, thresholds ->
                Organization(id, name, desc, logo.orElse(null), ranks, thresholds)
            }
        }
    }
}

data class OrgRank(
    val id: String,
    val name: String,
    val level: Int = 0
) {
    companion object {
        val CODEC: Codec<OrgRank> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter { it.id },
                Codec.STRING.fieldOf("name").forGetter { it.name },
                Codec.INT.optionalFieldOf("level", 0).forGetter { it.level }
            ).apply(instance, ::OrgRank)
        }
    }
}

data class RankThreshold(
    val reputation: Int,
    val rankId: String
) {
    companion object {
        val CODEC: Codec<RankThreshold> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("reputation").forGetter { it.reputation },
                Codec.STRING.fieldOf("rankId").forGetter { it.rankId }
            ).apply(instance, ::RankThreshold)
        }
    }
}
