package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Stores the current skin assignment for a player.
 * Persists through sessions so the skin is remembered on reconnect.
 *
 * @property skinName Name of the skin file (without .png), e.g. "steve_warrior"
 * @property skinModel "default" (Steve/wide arms) or "slim" (Alex/slim arms)
 */
data class PlayerSkinData(
    val skinName: String,
    val skinModel: String = "default"
) {
    val isSlim: Boolean get() = skinModel == "slim"

    companion object {
        val CODEC: Codec<PlayerSkinData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("skinName").forGetter { it.skinName },
                Codec.STRING.optionalFieldOf("skinModel", "default").forGetter { it.skinModel }
            ).apply(instance, ::PlayerSkinData)
        }
    }
}
