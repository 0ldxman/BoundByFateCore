package omc.boundbyfate.client.models.internal

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.ShaderProgram
import net.minecraft.client.render.RenderLayer
import net.minecraft.util.Identifier
import org.lwjgl.opengl.GL13
import omc.boundbyfate.client.mixin.accessor.ShaderProgramAccessor
import omc.boundbyfate.client.util.rl
import org.slf4j.LoggerFactory
import java.util.function.Function

// Shader utilities for NPC model rendering

private val shaderLogger = LoggerFactory.getLogger("BbfShaderUtil")

/**
 * Returns the OpenGL program reference (glRef) for this shader.
 * Uses mixin accessor since glRef is private in yarn 1.20.1.
 */
fun ShaderProgram.getProgramRef(): Int = (this as ShaderProgramAccessor).bbf_getGlRef()

inline fun drawWithShader(body: () -> Unit) {
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
 * Retrieves the entity_cutout_no_cull shader program from GameRenderer via reflection.
 *
 * Direct call to getRenderTypeEntityCutoutNoNullProgram() fails to compile with loom 1.15+
 * due to a remapping issue. We use the intermediary name (method_34504) instead.
 * At runtime MC classes are in intermediary, so this name is stable.
 */
private var shaderMethodResolved = false
private var shaderMethod: java.lang.reflect.Method? = null

private fun resolveShaderMethod(): java.lang.reflect.Method? {
    if (shaderMethodResolved) return shaderMethod
    shaderMethodResolved = true

    val gameRenderer = MinecraftClient.getInstance().gameRenderer
    val allMethods = buildList {
        var c: Class<*>? = gameRenderer.javaClass
        while (c != null) {
            addAll(c.declaredMethods)
            c = c.superclass
        }
    }

    val method = allMethods.firstOrNull { it.name == "method_34504" }
    if (method != null) {
        method.isAccessible = true
        shaderMethod = method
    } else {
        shaderLogger.warn("method_34504 not found in GameRenderer — shader will be null")
    }
    return shaderMethod
}

private fun getEntityCutoutNoNullProgram(): ShaderProgram? {
    return try {
        val method = resolveShaderMethod() ?: return null
        method.invoke(MinecraftClient.getInstance().gameRenderer) as? ShaderProgram
    } catch (e: Exception) {
        shaderLogger.error("Failed to invoke shader method", e)
        null
    }
}

val SHADER: ShaderProgram?
    get() = getEntityCutoutNoNullProgram()

val INSTANCED_SHADER: ShaderProgram?
    get() = getEntityCutoutNoNullProgram()

const val COLOR_MAP_INDEX = GL13.GL_TEXTURE0
const val NORMAL_MAP_INDEX = GL13.GL_TEXTURE1
const val SPECULAR_MAP_INDEX = GL13.GL_TEXTURE3
