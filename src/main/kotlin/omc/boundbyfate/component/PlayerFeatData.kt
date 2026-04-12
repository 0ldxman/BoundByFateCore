package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier

/**
 * Stores feats and ASI choices made by a player.
 *
 * @property feats Set of feat IDs the player has taken
 * @property statIncreases Accumulated ASI bonuses (stat ID -> total bonus points)
 * @property pendingUpgrades Number of unresolved ASI/Feat choice slots
 */
data class PlayerFeatData(
    val feats: Set<Identifier> = emptySet(),
    val statIncreases: Map<Identifier, Int> = emptyMap(),
    val pendingUpgrades: Int = 0
) {
    fun hasFeat(id: Identifier): Boolean = feats.contains(id)

    fun withFeat(id: Identifier): PlayerFeatData =
        copy(feats = feats + id)

    fun withStatIncrease(statId: Identifier, amount: Int): PlayerFeatData {
        val current = statIncreases[statId] ?: 0
        return copy(statIncreases = statIncreases + (statId to current + amount))
    }

    fun withPendingUpgrade(): PlayerFeatData =
        copy(pendingUpgrades = pendingUpgrades + 1)

    fun withResolvedUpgrade(): PlayerFeatData =
        copy(pendingUpgrades = (pendingUpgrades - 1).coerceAtLeast(0))

    companion object {
        val CODEC: Codec<PlayerFeatData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Identifier.CODEC.listOf()
                    .optionalFieldOf("feats", emptyList())
                    .forGetter { it.feats.toList() },
                Codec.unboundedMap(Identifier.CODEC, Codec.INT)
                    .optionalFieldOf("statIncreases", emptyMap())
                    .forGetter { it.statIncreases },
                Codec.INT
                    .optionalFieldOf("pendingUpgrades", 0)
                    .forGetter { it.pendingUpgrades }
            ).apply(instance) { feats, statIncreases, pendingUpgrades ->
                PlayerFeatData(feats.toSet(), statIncreases, pendingUpgrades)
            }
        }
    }
}
