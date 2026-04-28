package omc.boundbyfate.client.models.internal

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.irisshaders.iris.shadows.ShadowRenderer
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.TextureManager
import org.lwjgl.opengl.GL13
// ShaderInstanceAccessor mixin removed
// shouldOverrideShaders removed
// ModShaders removed - TODO: register shaders
import java.util.function.Function


inline fun drawWithShader(
    shader: net.minecraft.client.renderer.ShaderInstance = SHADER,
    state: RenderType = translucentShaderState(),
    body: () -> Unit,
) {
    val accessor = shader as ShaderInstanceAccessor

    state.setupRenderState()
    shader.setDefaultUniforms(
        VertexFormat.Mode.TRIANGLES,
        RenderSystem.getModelViewMatrix(),
        RenderSystem.getProjectionMatrix(),
        Minecraft.getInstance().window
    )
    shader.apply()

    accessor.samplerLocations().forEachIndexed { texture, index ->
        RenderSystem.glUniform1i(index, texture)
    }

    body()

    shader.clear()
    state.clearRenderState()
}

fun opaqueShaderState(): RenderType = RenderType.entityCutoutNoCull(TextureManager.INTENTIONAL_MISSING_TEXTURE)

fun translucentShaderState(): RenderType = RenderType.entityTranslucent(TextureManager.INTENTIONAL_MISSING_TEXTURE)

val batchingRenderType: Function<Material, RenderType> = Util.memoize<Material, RenderType> { material: Material ->
    val compositeState =
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEntityCutoutShader))
            .setTextureState(RenderStateShard.TextureStateShard(material.texture, false, false))
            .setTransparencyState(
                when (material.blend) {
                    Material.Blend.BLEND -> RenderStateShard.TRANSLUCENT_TRANSPARENCY
                    Material.Blend.OPAQUE -> RenderStateShard.NO_TRANSPARENCY
                }
            )
            .setCullState(if (material.doubleSided) RenderStateShard.NO_CULL else RenderStateShard.CULL)
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setOverlayState(RenderStateShard.OVERLAY)
            .createCompositeState(true)
    RenderType.create(
        "hollowengine:entity_cutout",
        DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.TRIANGLES,
        4096,
        true,
        false,
        compositeState
    )
}

val SHADER
    get() =
        if (ShadowRenderer.ACTIVE || false) GameRenderer.getRendertypeEntityCutoutShader()!!
        else net.minecraft.client.Minecraft.getInstance().gameRenderer.getRendertypeEntityCutoutShader()!! // Ванильный шейдер не поддерживает матрицу нормалей

val INSTANCED_SHADER
    get() = net.minecraft.client.Minecraft.getInstance().gameRenderer.getRendertypeEntityCutoutShader()!!

const val COLOR_MAP_INDEX = GL13.GL_TEXTURE0
const val NORMAL_MAP_INDEX = GL13.GL_TEXTURE1
const val SPECULAR_MAP_INDEX = GL13.GL_TEXTURE3



