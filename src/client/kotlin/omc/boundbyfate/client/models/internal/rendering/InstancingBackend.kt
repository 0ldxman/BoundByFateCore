package omc.boundbyfate.client.models.internal.rendering

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.ShaderProgram
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL33
import omc.boundbyfate.client.models.internal.*
import omc.boundbyfate.client.models.internal.manager.HollowModelManager

interface ModelInstancingBackend {
    fun canBatch(): Boolean = true

    fun flush(batches: Map<PipelineRenderer, List<SubmittedInstance>>)
}

object VanillaInstancingBackend : ModelInstancingBackend {
    override fun flush(batches: Map<PipelineRenderer, List<SubmittedInstance>>) {
        if (batches.isEmpty()) return

        withInstancingRenderState {
            drawWithShader {
                renderOpaque(batches, INSTANCED_SHADER ?: return@drawWithShader) { renderer, instances, shader ->
                    if (renderer.shouldUseInstancing(instances.size)) {
                        renderer.renderInstanced(instances, shader, InstancedShaderLayoutMode.FIXED)
                    } else {
                        fallbackToCapturedDraws(renderer, instances, shader)
                    }
                }
            }
            drawWithShader {
                renderTranslucent(batches, INSTANCED_SHADER ?: return@drawWithShader) { renderer, instances, shader ->
                    if (renderer.shouldUseInstancing(instances.size)) {
                        renderer.renderInstanced(instances, shader, InstancedShaderLayoutMode.FIXED)
                    } else {
                        fallbackToCapturedDraws(renderer, instances, shader)
                    }
                }
            }
        }
    }
}

inline fun withInstancingRenderState(body: () -> Unit) {
    val currentVao = GL33.glGetInteger(GL33.GL_VERTEX_ARRAY_BINDING)
    val currentElementArrayBuffer = GL33.glGetInteger(GL33.GL_ELEMENT_ARRAY_BUFFER_BINDING)
    val shaderTexture0 = RenderSystem.getShaderTexture(0)
    val shaderTexture1 = RenderSystem.getShaderTexture(1)
    val shaderTexture2 = RenderSystem.getShaderTexture(2)
    val savedActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)

    // Save current texture bindings via GL directly
    RenderSystem.activeTexture(GL13.GL_TEXTURE2)
    val texture2 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
    RenderSystem.bindTexture(HollowModelManager.lightTexture.getGlId())
    RenderSystem.setShaderTexture(2, HollowModelManager.lightTexture.getGlId())

    RenderSystem.activeTexture(GL13.GL_TEXTURE1)
    val texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
    MinecraftClient.getInstance().gameRenderer.overlayTexture.setupOverlayColor()
    val overlayTextureId = RenderSystem.getShaderTexture(1)
    RenderSystem.bindTexture(overlayTextureId)
    RenderSystem.setShaderTexture(1, overlayTextureId)
    MinecraftClient.getInstance().gameRenderer.overlayTexture.teardownOverlayColor()

    RenderSystem.activeTexture(GL13.GL_TEXTURE0)
    val texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)

    try {
        body()
    } finally {
        RenderSystem.setShaderTexture(0, shaderTexture0)
        RenderSystem.setShaderTexture(1, shaderTexture1)
        RenderSystem.setShaderTexture(2, shaderTexture2)
        RenderSystem.activeTexture(GL13.GL_TEXTURE2)
        RenderSystem.bindTexture(texture2)
        RenderSystem.activeTexture(GL13.GL_TEXTURE1)
        RenderSystem.bindTexture(texture1)
        RenderSystem.activeTexture(GL13.GL_TEXTURE0)
        RenderSystem.bindTexture(texture0)
        RenderSystem.activeTexture(savedActiveTexture)

        RenderSystem.glBindVertexArray(currentVao)
        RenderSystem.glBindBuffer(GL33.GL_ELEMENT_ARRAY_BUFFER, currentElementArrayBuffer)

        com.mojang.blaze3d.platform.GlStateManager._glUseProgram(0)
    }
}

inline fun renderOpaque(
    batches: Map<PipelineRenderer, List<SubmittedInstance>>,
    shader: ShaderProgram,
    render: (PipelineRenderer, List<SubmittedInstance>, ShaderProgram) -> Unit,
) {
    for ((renderer, instances) in batches.entries.asSequence().filter { !it.key.isTranslucent }) {
        render(renderer, instances, shader)
    }
}

inline fun renderTranslucent(
    batches: Map<PipelineRenderer, List<SubmittedInstance>>,
    shader: ShaderProgram,
    render: (PipelineRenderer, List<SubmittedInstance>, ShaderProgram) -> Unit,
) {
    for ((renderer, instances) in batches.entries
        .asSequence()
        .filter { it.key.isTranslucent }
        .sortedByDescending { (_, instances) -> instances.maxOfOrNull(SubmittedInstance::sortKey) ?: Float.NEGATIVE_INFINITY }) {
        render(renderer, instances, shader)
    }
}

fun fallbackToCapturedDraws(
    renderer: PipelineRenderer,
    instances: List<SubmittedInstance>,
    shader: ShaderProgram? = SHADER,
) {
    val s = shader ?: return
    val drawInstances = if (renderer.isTranslucent) instances.sortedByDescending(SubmittedInstance::sortKey) else instances
    for (instance in drawInstances) {
        renderer.renderCapturedInstance(instance, s)
    }
}

enum class InstancedShaderLayoutMode {
    FIXED,
    RUNTIME,
}
