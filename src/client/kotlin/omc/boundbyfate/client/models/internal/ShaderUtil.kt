package omc.boundbyfate.client.models.internal

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.ShaderProgram
import net.minecraft.client.render.RenderLayer
import net.minecraft.util.Identifier
import org.lwjgl.opengl.GL13
import omc.boundbyfate.client.util.rl
import java.util.function.Function

// Shader utilities for NPC model rendering

inline fun drawWithShader(
    body: () -> Unit,
) {
    body()
}

fun opaqueRenderLayer(texture: Identifier): RenderLayer =
    RenderLayer.getEntityCutoutNoCull(texture)

fun translucentRenderLayer(texture: Identifier): RenderLayer =
    RenderLayer.getEntityTranslucent(texture)

val batchingRenderType: Function<Material, RenderLayer> = Function { material ->
    if (material.blend == Material.Blend.BLEND) {
        RenderLayer.getEntityTranslucent(material.texture)
    } else {
        RenderLayer.getEntityCutoutNoCull(material.texture)
    }
}

val SHADER: ShaderProgram?
    get() = MinecraftClient.getInstance().gameRenderer.getProgram("rendertype_entity_cutout")

val INSTANCED_SHADER: ShaderProgram?
    get() = MinecraftClient.getInstance().gameRenderer.getProgram("rendertype_entity_cutout")

const val COLOR_MAP_INDEX = GL13.GL_TEXTURE0
const val NORMAL_MAP_INDEX = GL13.GL_TEXTURE1
const val SPECULAR_MAP_INDEX = GL13.GL_TEXTURE3
