package omc.boundbyfate.client.render.textures

import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.server.packs.resources.ResourceManager

class GlTexture(id: Int): AbstractTexture() {
    init {
        this.id = id
    }
    override fun load(pResourceManager: ResourceManager) {}
}


