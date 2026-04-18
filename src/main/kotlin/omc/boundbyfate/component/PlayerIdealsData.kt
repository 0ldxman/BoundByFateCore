package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import omc.boundbyfate.api.identity.Alignment
import omc.boundbyfate.api.identity.IdealAlignment

/**
 * Represents a single ideal (убеждение) of a character.
 * Can be tied to an alignment axis to check compatibility.
 */
data class Ideal(
    val id: String,           // UUID string
    val text: String,
    val alignmentAxis: IdealAlignment = IdealAlignment.ANY
) {
    /**
     * Checks if this ideal is compatible with the given alignment.
     */
    fun isCompatibleWith(alignment: Alignment): Boolean =
        alignmentAxis.isCompatibleWith(alignment)

    companion object {
        val CODEC: Codec<Ideal> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter(Ideal::id),
                Codec.STRING.fieldOf("text").forGetter(Ideal::text),
                Codec.STRING.fieldOf("alignmentAxis").xmap(
                    { IdealAlignment.valueOf(it) },
                    { it.name }
                ).forGetter(Ideal::alignmentAxis)
            ).apply(instance, ::Ideal)
        }
    }
}

/**
 * Represents a single flaw (слабость) of a character.
 * Purely descriptive — no mechanical effect at this stage.
 */
data class Flaw(
    val id: String,           // UUID string
    val text: String
) {
    companion object {
        val CODEC: Codec<Flaw> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter(Flaw::id),
                Codec.STRING.fieldOf("text").forGetter(Flaw::text)
            ).apply(instance, ::Flaw)
        }
    }
}

/**
 * Container for all ideals and flaws of a player.
 */
data class PlayerIdealsData(
    val ideals: List<Ideal> = emptyList(),
    val flaws: List<Flaw> = emptyList()
) {
    companion object {
        val CODEC: Codec<PlayerIdealsData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.list(Ideal.CODEC).optionalFieldOf("ideals", emptyList()).forGetter(PlayerIdealsData::ideals),
                Codec.list(Flaw.CODEC).optionalFieldOf("flaws", emptyList()).forGetter(PlayerIdealsData::flaws)
            ).apply(instance, ::PlayerIdealsData)
        }
    }
}
