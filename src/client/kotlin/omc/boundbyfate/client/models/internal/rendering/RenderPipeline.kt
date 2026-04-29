package omc.boundbyfate.client.models.internal.rendering

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import de.fabmax.kool.math.MutableMat3f
import de.fabmax.kool.math.Vec3f
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.util.math.MatrixStack
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL33
import omc.boundbyfate.client.models.internal.drawWithShader
import omc.boundbyfate.client.models.internal.manager.HollowModelManager

typealias Renderable = RenderContext.() -> Unit

interface RenderPipeline {
    fun addBatchedRenderable(action: Renderable)
    fun addInstancedRenderable(action: Renderable)
    fun addVAORenderable(action: Renderable)
    fun onUpdate(action: () -> Unit)
    fun addSkinnable(action: () -> Unit)


    fun render(context: RenderContext) {}

    fun clear()
}


data class RenderContext(
    val stack: MatrixStack,
    val source: VertexConsumerProvider,
    val light: Int,
    val overlay: Int,
    val allowInstancing: Boolean = false,
    val openedBatchedRenderTypes: MutableSet<RenderLayer>? = null,
)


class ListRenderPipeline : RenderPipeline {
    private val batchedCommands = ArrayList<Renderable>()
    private val instancedCommands = ArrayList<Renderable>()
    private val vaoCommands = ArrayList<Renderable>()
    private val commands = ArrayList<() -> Unit>()
    private val skinCommands = ArrayList<() -> Unit>()

    override fun addBatchedRenderable(action: Renderable) {
        batchedCommands.add(action)
    }

    override fun addInstancedRenderable(action: Renderable) {
        instancedCommands.add(action)
    }

    override fun addVAORenderable(action: Renderable) {
        vaoCommands.add(action)
    }

    override fun onUpdate(action: () -> Unit) {
        commands.add(action)
    }

    override fun addSkinnable(action: () -> Unit) {
        skinCommands.add(action)
    }

    override fun render(context: RenderContext) {
        for (action in commands) action()
        for (action in batchedCommands) action(context)
        if (instancedCommands.isNotEmpty()) renderInstanced(context)
        if (vaoCommands.isNotEmpty()) renderVAO(context)
    }

    override fun clear() {
        commands.clear()
        batchedCommands.clear()
        instancedCommands.clear()
        vaoCommands.clear()
        skinCommands.clear()
    }

    fun renderVAO(context: RenderContext) {
        val activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)

        //Получение текущих VAO и IBO
        val currentVAO = GL33.glGetInteger(GL33.GL_VERTEX_ARRAY_BINDING)
        val currentElementArrayBuffer = GL33.glGetInteger(GL33.GL_ELEMENT_ARRAY_BUFFER_BINDING)

        transformSkinning()

        GL33.glVertexAttribI2i(3, context.overlay and FFFF, context.overlay shr 16 and FFFF) // Оверлей при ударе
        GL33.glVertexAttribI2i(4, context.light and FFFF, context.light shr 16 and FFFF) // Освещение

        RenderSystem.activeTexture(GL13.GL_TEXTURE2)
        val texture2 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        RenderSystem.bindTexture(HollowModelManager.lightTexture.getGlId())
        RenderSystem.activeTexture(GL13.GL_TEXTURE1)
        val texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        MinecraftClient.getInstance().gameRenderer.overlayTexture().setupOverlayColor()
        RenderSystem.bindTexture(RenderSystem.getShaderTexture(1))
        MinecraftClient.getInstance().gameRenderer.overlayTexture().teardownOverlayColor()
        RenderSystem.activeTexture(GL13.GL_TEXTURE0)

        val texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)

        drawWithShader {
            for (draw in vaoCommands) draw(context)
        }

        RenderSystem.activeTexture(GL13.GL_TEXTURE2)
        RenderSystem.bindTexture(texture2)

        RenderSystem.activeTexture(GL13.GL_TEXTURE1)
        RenderSystem.bindTexture(texture1)

        RenderSystem.activeTexture(GL13.GL_TEXTURE0)
        RenderSystem.bindTexture(texture)

        RenderSystem.activeTexture(activeTexture)

        RenderSystem.glBindVertexArray(currentVAO)
        RenderSystem.glBindBuffer(GL33.GL_ELEMENT_ARRAY_BUFFER, currentElementArrayBuffer)

        GlStateManager._glUseProgram(0)
    }

    fun renderInstanced(context: RenderContext) {
        val activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
        val currentVAO = GL33.glGetInteger(GL33.GL_VERTEX_ARRAY_BINDING)
        val currentElementArrayBuffer = GL33.glGetInteger(GL33.GL_ELEMENT_ARRAY_BUFFER_BINDING)

        GL33.glVertexAttribI2i(3, context.overlay and FFFF, context.overlay shr 16 and FFFF)
        GL33.glVertexAttribI2i(4, context.light and FFFF, context.light shr 16 and FFFF)

        RenderSystem.activeTexture(GL13.GL_TEXTURE2)
        val texture2 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        RenderSystem.bindTexture(HollowModelManager.lightTexture.getGlId())
        RenderSystem.activeTexture(GL13.GL_TEXTURE1)
        val texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        MinecraftClient.getInstance().gameRenderer.overlayTexture().setupOverlayColor()
        RenderSystem.bindTexture(RenderSystem.getShaderTexture(1))
        MinecraftClient.getInstance().gameRenderer.overlayTexture().teardownOverlayColor()
        RenderSystem.activeTexture(GL13.GL_TEXTURE0)

        val texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)

        drawWithShader {
            for (draw in instancedCommands) draw(context)
        }

        RenderSystem.activeTexture(GL13.GL_TEXTURE2)
        RenderSystem.bindTexture(texture2)
        RenderSystem.activeTexture(GL13.GL_TEXTURE1)
        RenderSystem.bindTexture(texture1)
        RenderSystem.activeTexture(GL13.GL_TEXTURE0)
        RenderSystem.bindTexture(texture)
        RenderSystem.activeTexture(activeTexture)

        RenderSystem.glBindVertexArray(currentVAO)
        RenderSystem.glBindBuffer(GL33.GL_ELEMENT_ARRAY_BUFFER, currentElementArrayBuffer)

        GlStateManager._glUseProgram(0)
    }

    private fun transformSkinning() {
        if (skinCommands.isNotEmpty()) {
            GlStateManager._glUseProgram(HollowModelManager.glProgramSkinning)
            GL33.glEnable(GL33.GL_RASTERIZER_DISCARD)
            for (skinCommand in skinCommands) skinCommand()
            GL33.glBindBuffer(GL33.GL_TEXTURE_BUFFER, 0)
            GL33.glDisable(GL33.GL_RASTERIZER_DISCARD)
        }
    }


    companion object {
        private const val FFFF = '\uffff'.code
    }
}



