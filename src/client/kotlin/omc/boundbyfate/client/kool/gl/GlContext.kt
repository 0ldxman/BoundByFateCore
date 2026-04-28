package omc.boundbyfate.client.kool.gl

import com.mojang.blaze3d.platform.GlStateManager
import de.fabmax.kool.input.Input
import de.fabmax.kool.pipeline.CullMethod
import de.fabmax.kool.pipeline.DepthCompareOp
import de.fabmax.kool.pipeline.backend.gl.glOp
import de.fabmax.kool.scene.Scene
import kotlinx.coroutines.runBlocking
import net.minecraft.client.MinecraftClient
import omc.boundbyfate.client.kool.KoolHooks
import omc.boundbyfate.client.kool.KoolManager
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL33

object GlContext {
    private var activeTexture = -1
    private var bindingTexture = -1

    private var activeVao = -1
    private var activeEbo = -1

    private var depthState = false
    private var depthMode = -1
    private var depthMask = false
    private var blendState = false
    private var blendMode = -1
    private var cullState = false
    private var cullMode = -1

    fun setupState() {
        activeTexture = GlStateManager._getActiveTexture()
        bindingTexture = GL30.glGetInteger(GL30.GL_TEXTURE_BINDING_2D)
        activeVao = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        activeEbo = GL30.glGetInteger(GL30.GL_ELEMENT_ARRAY_BUFFER_BINDING)
        depthState = GL30.glIsEnabled(GL30.GL_DEPTH_TEST)
        depthMode = GL30.glGetInteger(GL30.GL_DEPTH_FUNC)
        depthMask = GL30.glGetBoolean(GL30.GL_DEPTH_WRITEMASK)
        blendState = GL30.glIsEnabled(GL30.GL_BLEND)
        blendMode = GL30.glGetInteger(GL30.GL_BLEND_SRC_RGB)
        cullState = GL30.glIsEnabled(GL30.GL_CULL_FACE)
        cullMode = GL30.glGetInteger(GL30.GL_CULL_FACE_MODE)

        MCGlApi.depthMask(KoolHooks.getGlStateIsWriteDepth())
        val actDepthTest = KoolHooks.getGlStateDepthTest()
        if (actDepthTest == DepthCompareOp.ALWAYS) {
            MCGlApi.disable(MCGlApi.DEPTH_TEST)
        } else {
            MCGlApi.enable(MCGlApi.DEPTH_TEST)
            actDepthTest?.glOp(MCGlApi)?.let(MCGlApi::depthFunc)
        }
        when (KoolHooks.getGlStateCullMethod()) {
            CullMethod.CULL_BACK_FACES -> {
                MCGlApi.enable(MCGlApi.CULL_FACE)
                MCGlApi.cullFace(MCGlApi.BACK)
            }
            CullMethod.CULL_FRONT_FACES -> {
                MCGlApi.enable(MCGlApi.CULL_FACE)
                MCGlApi.cullFace(MCGlApi.FRONT)
            }
            else -> MCGlApi.disable(MCGlApi.CULL_FACE)
        }
        KoolHooks.resetShaders(KoolManager.context)
    }

    fun restoreState() {
        GL30.glActiveTexture(activeTexture)
        GL33.glBindTexture(GL33.GL_TEXTURE_2D, bindingTexture)
        GL30.glBindVertexArray(activeVao)
        GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, activeEbo)
        if (depthState) {
            MCGlApi.enable(MCGlApi.DEPTH_TEST)
            GL30.glDepthFunc(depthMode)
        } else {
            GL30.glDepthFunc(depthMode)
            MCGlApi.disable(MCGlApi.DEPTH_TEST)
        }
        GL30.glDepthMask(depthMask)
        if (blendState) {
            MCGlApi.enable(MCGlApi.BLEND)
            GL30.glBlendFuncSeparate(blendMode, GL30.GL_ONE_MINUS_SRC_ALPHA, GL30.GL_ONE, GL30.GL_ONE_MINUS_SRC_ALPHA)
        } else {
            GL30.glBlendFuncSeparate(blendMode, GL30.GL_ONE_MINUS_SRC_ALPHA, GL30.GL_ONE, GL30.GL_ONE_MINUS_SRC_ALPHA)
            MCGlApi.disable(MCGlApi.BLEND)
        }
        if (cullState) {
            MCGlApi.enable(MCGlApi.CULL_FACE)
            GL30.glCullFace(cullMode)
        } else {
            GL30.glCullFace(cullMode)
            MCGlApi.disable(MCGlApi.CULL_FACE)
        }
        MinecraftClient.getInstance().framebuffer.beginWrite(true)
    }

    inline fun <T> withState(block: () -> T): T {
        setupState()
        return try {
            block()
        } finally {
            restoreState()
        }
    }
}

fun renderFrame() {
    KoolHooks.setDeltaT(1f / 20f)
    KoolHooks.addGameTime(1.0 / 20.0)
    KoolHooks.incrementFrameCount()

    Input.poll(KoolManager.context)
    KoolHooks.executeCoroutineTasks()

    KoolManager.context.renderFrame()
}

fun Scene.render(recordState: Boolean = true) {
    if (recordState) GlContext.setupState()
    assert(this in KoolManager.context.scenes) { "Scene ${this.name} must be added in KoolManager.context" }
    KoolManager.context.backend.renderMCFrame(KoolManager.context)
    if (recordState) GlContext.restoreState()
}
