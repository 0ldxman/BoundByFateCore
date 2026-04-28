package omc.boundbyfate.client.kool

import de.fabmax.kool.pipeline.MipMapping
import de.fabmax.kool.pipeline.SamplerSettings
import de.fabmax.kool.pipeline.Texture
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.backend.gl.GlTexture
import de.fabmax.kool.pipeline.backend.gl.LoadedTextureGl
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.Framebuffer
import omc.boundbyfate.client.kool.gl.MCGlApi

// Offscreen framebuffer for GUI rendering (512x512)
internal val guiFramebuffer: Framebuffer by lazy {
    val fb = net.minecraft.client.gl.SimpleFramebuffer(512, 512, true, MinecraftClient.IS_SYSTEM_MAC)
    fb.setClearColor(0f, 0f, 0f, 0f)
    fb
}

val WINDOW_BUFFER by lazy { createFramebufferTexture(guiFramebuffer) }

fun createFramebufferTexture(framebuffer: Framebuffer) = Texture2d(
    mipMapping = MipMapping.Off,
    samplerSettings = SamplerSettings().clamped().nearest()
).apply {
    val estSize = Texture.estimatedTexSize(framebuffer.textureWidth, framebuffer.textureHeight, 1, 1, 4).toLong()
    gpuTexture = LoadedTextureGl(
        MCGlApi.TEXTURE_2D,
        GlTexture(framebuffer.colorAttachment),
        MCGlApi.backend,
        this,
        estSize
    ).apply {
        width = framebuffer.textureWidth
        height = framebuffer.textureHeight
    }
}

fun onResize(width: Int, height: Int) {
    (WINDOW_BUFFER.gpuTexture as? LoadedTextureGl)?.apply {
        this.width = width
        this.height = height
    }
}
