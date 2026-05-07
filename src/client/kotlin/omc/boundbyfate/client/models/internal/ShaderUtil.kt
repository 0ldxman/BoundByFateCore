package omc.boundbyfate.client.models.internal

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.ShaderProgram
import net.minecraft.client.render.RenderLayer
import net.minecraft.util.Identifier
import org.lwjgl.opengl.GL13
import omc.boundbyfate.client.mixin.accessor.ShaderProgramAccessor
import omc.boundbyfate.client.util.rl
import java.util.function.Function

// Shader utilities for NPC model rendering

/**
 * Returns the OpenGL program reference (glRef) for this shader.
 * Uses mixin accessor since glRef is private in yarn 1.20.1.
 */
fun ShaderProgram.getProgramRef(): Int = (this as ShaderProgramAccessor).bbf_getGlRef()

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

/**
 * Retrieves the entity_cutout_no_cull shader program from GameRenderer.
 *
 * In loom 1.15+ with MC 1.20.1, the yarn-mapped method getRenderTypeEntityCutoutNoNullProgram()
 * is not accessible via direct call due to a remapping issue. We use reflection with the
 * intermediary name (method_34504) as a workaround.
 */
private fun getEntityCutoutNoNullProgram(): ShaderProgram? {
    return try {
        val gameRenderer = MinecraftClient.getInstance().gameRenderer
        // intermediary name for getRenderTypeEntityCutoutNoNullProgram in 1.20.1
        val method = gameRenderer.javaClass.getMethod("method_34504")
        method.isAccessible = true
        method.invoke(gameRenderer) as? ShaderProgram
    } catch (_: Exception) {
        // fallback: return whatever shader is currently active
        RenderSystem.getShader()
    }
}

val SHADER: ShaderProgram?
    get() = getEntityCutoutNoNullProgram()

val INSTANCED_SHADER: ShaderProgram?
    get() = getEntityCutoutNoNullProgram()

const val COLOR_MAP_INDEX = GL13.GL_TEXTURE0
const val NORMAL_MAP_INDEX = GL13.GL_TEXTURE1
const val SPECULAR_MAP_INDEX = GL13.GL_TEXTURE3
