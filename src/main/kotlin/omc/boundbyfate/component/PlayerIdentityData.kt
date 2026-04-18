package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class PlayerIdentityData(
    val alignment: AlignmentData = AlignmentData(),
    val idealsData: PlayerIdealsData = PlayerIdealsData(),
    val motivationData: PlayerMotivationData = PlayerMotivationData()
) {
    companion object {
        val CODEC: Codec<PlayerIdentityData> = RecordCodecBuilder.create { instance ->
            instance.group(
                AlignmentData.CODEC.fieldOf("alignment").forGetter(PlayerIdentityData::alignment),
                PlayerIdealsData.CODEC.optionalFieldOf("idealsData", PlayerIdealsData()).forGetter(PlayerIdentityData::idealsData),
                PlayerMotivationData.CODEC.optionalFieldOf("motivationData", PlayerMotivationData()).forGetter(PlayerIdentityData::motivationData)
            ).apply(instance, ::PlayerIdentityData)
        }
    }
}
