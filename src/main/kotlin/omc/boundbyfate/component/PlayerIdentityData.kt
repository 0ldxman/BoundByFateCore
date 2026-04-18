package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import dev.onyxstudios.cca.api.v3.component.Component

/**
 * Main component storing all player identity data:
 * - Alignment
 * - Ideals & Flaws
 * - Motivations & Goals (future)
 * - Relationships (future)
 * - Faction standings (future)
 */
data class PlayerIdentityData(
    val alignment: AlignmentData = AlignmentData(),
    val idealsData: PlayerIdealsData = PlayerIdealsData()
    // TODO: Add motivations, goals, relationships, factions in future phases
) : Component {

    companion object {
        val CODEC: Codec<PlayerIdentityData> = RecordCodecBuilder.create { instance ->
            instance.group(
                AlignmentData.CODEC.fieldOf("alignment").forGetter(PlayerIdentityData::alignment),
                PlayerIdealsData.CODEC.optionalFieldOf("idealsData", PlayerIdealsData()).forGetter(PlayerIdentityData::idealsData)
            ).apply(instance, ::PlayerIdentityData)
        }
    }
}
