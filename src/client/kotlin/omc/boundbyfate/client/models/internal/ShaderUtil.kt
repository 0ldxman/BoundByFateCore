package omc.boundbyfate.client.models.internal

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.ShaderProgram
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexFormats
import net.minecraft.util.Identifier
import org.lwjgl.opengl.GL13
import omc.boundbyfate.client.models.internal.rendering.RenderContext
import omc.boundbyfate.client.util.rl

// Shader utilities for NPC model rendering

fun opaqueRenderLayer(texture: Identifier): RenderLayer =
    RenderLayer.getEntityCutoutNoCull(texture)

fun translucentRenderLayer(texture: Identifier): RenderLayer =
    RenderLayer.getEntityTranslucent(texture)

val SHADER: ShaderProgram?
    get() = MinecraftClient.getInstance().gameRenderer.getProgram("rendertype_entity_cutout")

val INSTANCED_SHADER: ShaderProgram?
    get() = MinecraftClient.getInstance().gameRenderer.getProgram("rendertype_entity_cutout")

const val COLOR_MAP_INDEX = GL13.GL_TEXTURE0
const val NORMAL_MAP_INDEX = GL13.GL_TEXTURE1
const val SPECULAR_MAP_INDEX = GL13.GL_TEXTURE3

/**
 * Batching render type selector - returns appropriate RenderLayer for a material.
 */
val RenderContext.batchingRenderType: java.util.function.Function<Material, RenderLayer>
    get() = java.util.function.Function { material ->
        when (material.blend) {
            Material.Blend.OPAQUE -> RenderLayer.getEntityCutoutNoCull(material.texture)
            Material.Blend.BLEND -> RenderLayer.getEntityTranslucent(material.texture)
        }
    }

/**
 * Execute a block with the given shader active.
 */
inline fun drawWithShader(shader: ShaderProgram? = SHADER, shaderSetup: (() -> Unit)? = null, block: () -> Unit) {
    val prev = RenderSystem.getShader()
    shaderSetup?.invoke()
    if (shader != null) {
        RenderSystem.setShader { shader }
    }
    try {
        block()
    } finally {
        RenderSystem.setShader { prev }
    }
}

/**
 * Opaque shader state setup.
 */
fun opaqueShaderState(): (() -> Unit) = {
    RenderSystem.disableBlend()
    RenderSystem.enableDepthTest()
}

/**
 * Translucent shader state setup.
 */
fun translucentShaderState(): (() -> Unit) = {
    RenderSystem.enableBlend()
    RenderSystem.defaultBlendFunc()
    RenderSystem.enableDepthTest()
}
