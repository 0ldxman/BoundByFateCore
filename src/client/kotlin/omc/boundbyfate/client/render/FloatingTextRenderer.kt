package omc.boundbyfate.client.render

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import net.minecraft.util.math.Vec3d
import org.joml.Matrix4f

/**
 * Renders floating text labels in world space.
 * Used to show attack roll results above targets (only visible to the attacker).
 *
 * Each entry floats upward and fades out over its lifetime.
 */
object FloatingTextRenderer {

    private const val LIFETIME_TICKS = 40  // 2 seconds
    private const val FLOAT_SPEED = 0.015f // blocks per tick upward drift

    private val entries = mutableListOf<FloatingTextEntry>()

    data class FloatingTextEntry(
        val text: String,
        val color: Int,          // ARGB
        var x: Double,
        var y: Double,
        var z: Double,
        var ticksLeft: Int = LIFETIME_TICKS
    )

    fun add(text: String, color: Int, x: Double, y: Double, z: Double) {
        entries.add(FloatingTextEntry(text, color, x, y, z))
    }

    fun tick() {
        val iter = entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            entry.ticksLeft--
            entry.y += FLOAT_SPEED
            if (entry.ticksLeft <= 0) iter.remove()
        }
    }

    fun render(context: WorldRenderContext) {
        if (entries.isEmpty()) return

        val client = MinecraftClient.getInstance()
        val textRenderer = client.textRenderer
        val camera = context.camera()
        val cameraPos = camera.pos
        val matrices = context.matrixStack() ?: return
        val consumers = context.consumers() ?: return

        for (entry in entries) {
            val alpha = (entry.ticksLeft.toFloat() / LIFETIME_TICKS * 255).toInt().coerceIn(0, 255)
            val color = (alpha shl 24) or (entry.color and 0x00FFFFFF)

            matrices.push()
            matrices.translate(
                entry.x - cameraPos.x,
                entry.y - cameraPos.y,
                entry.z - cameraPos.z
            )

            // Billboard: face the camera
            matrices.multiply(camera.rotation)
            matrices.scale(-0.025f, -0.025f, 0.025f)

            val matrix: Matrix4f = matrices.peek().positionMatrix
            val text = Text.literal(entry.text)
            val w = textRenderer.getWidth(text)

            textRenderer.draw(
                text,
                (-w / 2).toFloat(),
                0f,
                color,
                false,
                matrix,
                consumers,
                net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL,
                0,
                0xF000F0
            )

            matrices.pop()
        }
    }
}
