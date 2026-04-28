package omc.boundbyfate.client.render.textures

import net.minecraft.client.texture.AbstractTexture
import net.minecraft.resource.ResourceManager

class GlTexture(val handle: Int) : AbstractTexture() {
    init {
        this.glId = handle
    }

    override fun load(resourceManager: ResourceManager) = Unit
}


