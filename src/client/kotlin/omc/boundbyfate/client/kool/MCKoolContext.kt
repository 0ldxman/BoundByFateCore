package omc.boundbyfate.client.kool

import de.fabmax.kool.KoolContext
import de.fabmax.kool.input.CursorShape
import de.fabmax.kool.input.PlatformInputJvm
import net.minecraft.client.MinecraftClient
import org.lwjgl.glfw.GLFW.*
import omc.boundbyfate.client.kool.gl.MCRenderBackendGl
import java.awt.Desktop
import java.net.URI

class MCKoolContext : KoolContext() {

    init {
        KoolHooks.createContext(this)
        isWindowFocused = true

        val shapesField = PlatformInputJvm::class.java.getDeclaredField("cursorShapes")
        shapesField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = shapesField.get(PlatformInputJvm) as MutableMap<CursorShape, Long>
        createStandardCursors(map)
        val xScale = FloatArray(1)
        val yScale = FloatArray(1)
        glfwGetWindowContentScale(MinecraftClient.getInstance().window.handle, xScale, yScale)
        KoolHooks.setScale(this, xScale[0])
    }

    override var renderScale = 1f

    private fun createStandardCursors(cursorShapes: MutableMap<CursorShape, Long>): MutableMap<CursorShape, Long> {
        cursorShapes[CursorShape.DEFAULT] = 0L
        cursorShapes[CursorShape.TEXT] = glfwCreateStandardCursor(GLFW_IBEAM_CURSOR)
        cursorShapes[CursorShape.CROSSHAIR] = glfwCreateStandardCursor(GLFW_CROSSHAIR_CURSOR)
        cursorShapes[CursorShape.HAND] = glfwCreateStandardCursor(GLFW_HAND_CURSOR)
        cursorShapes[CursorShape.NOT_ALLOWED] = glfwCreateStandardCursor(GLFW_NOT_ALLOWED_CURSOR)
        cursorShapes[CursorShape.RESIZE_EW] = glfwCreateStandardCursor(GLFW_RESIZE_EW_CURSOR)
        cursorShapes[CursorShape.RESIZE_NS] = glfwCreateStandardCursor(GLFW_RESIZE_NS_CURSOR)
        cursorShapes[CursorShape.RESIZE_NESW] = glfwCreateStandardCursor(GLFW_RESIZE_NESW_CURSOR)
        cursorShapes[CursorShape.RESIZE_NWSE] = glfwCreateStandardCursor(GLFW_RESIZE_NWSE_CURSOR)
        cursorShapes[CursorShape.RESIZE_ALL] = glfwCreateStandardCursor(GLFW_RESIZE_ALL_CURSOR)
        return cursorShapes
    }

    override val backend = MCRenderBackendGl(this)
    private val mcWindow = MinecraftClient.getInstance().window

    override var isFullscreen: Boolean
        get() = mcWindow.isFullscreen
        set(value) {
            if (mcWindow.isFullscreen != value) mcWindow.toggleFullscreen()
        }
    override val windowHeight: Int get() = mcWindow.height
    override val windowWidth: Int get() = mcWindow.width

    override fun getSysInfos() = emptyList<String>()

    override fun openUrl(url: String, sameWindow: Boolean) {
        Desktop.getDesktop().browse(URI(url))
    }

    override fun run() {}

    fun renderFrame() {
        render(0.0)
        backend.renderFrame(this)
    }
}
