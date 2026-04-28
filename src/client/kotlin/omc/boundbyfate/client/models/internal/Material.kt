package omc.boundbyfate.client.models.internal

import de.fabmax.kool.util.Color
import net.minecraft.util.Identifier
// HollowCore removed
import omc.boundbyfate.client.util.rl

data class Material(
    var color: Color = Color(1f, 1f, 1f, 1f),
    var texture: Identifier = MISSING_TEXTURE,
    var normalTexture: Identifier = MISSING_NORMAL,
    var specularTexture: Identifier = MISSING_SPECULAR,
    var doubleSided: Boolean = false,
    var blend: Blend = Blend.OPAQUE
) {
    enum class Blend { OPAQUE, BLEND }

    companion object {
        val MISSING_TEXTURE = "boundbyfate-core:default_color_map".rl
        val MISSING_NORMAL = "boundbyfate-core:default_normal_map".rl
        val MISSING_SPECULAR = "boundbyfate-core:default_specular_map".rl
    }
}


