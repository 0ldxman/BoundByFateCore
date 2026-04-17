package omc.boundbyfate.client.render

import ladysnake.satin.api.event.ShaderEffectRenderCallback
import ladysnake.satin.api.managed.ManagedShaderEffect
import ladysnake.satin.api.managed.ShaderEffectManager
import net.minecraft.client.MinecraftClient
import net.minecraft.util.Identifier
import omc.boundbyfate.client.state.DarkvisionState

/**
 * Darkvision renderer using gamma boost + desaturation shader.
 *
 * Gamma boost brightens dark areas (like vanilla night vision).
 * Shader applies grayscale desaturation to dark pixels.
 */
object DarkvisionRenderer {

    private val SHADER_ID = Identifier("boundbyfate-core", "shaders/post/darkvision.json")
    private val shader: ManagedShaderEffect = ShaderEffectManager.getInstance().manage(SHADER_ID)

    var shouldRender = false
        private set

    private var originalGamma: Double = 1.0
    private var gammaModified = false

    fun register() {
        ShaderEffectRenderCallback.EVENT.register { tickDelta ->
            if (shouldRender) {
                try {
                    val rangeBlocks = DarkvisionState.rangeFt / 5.0f * 1.5f
                    shader.findUniform1f("DarkvisionRange")?.set(rangeBlocks)
                } catch (e: Exception) { /* shader not loaded */ }
                shader.render(tickDelta)
            }
        }
    }

    fun tick(client: MinecraftClient) {
        val world = client.world
        val player = client.player

        if (world == null || player == null || !DarkvisionState.hasDarkvision) {
            shouldRender = false
            // Restore original gamma
            if (gammaModified) {
                client.options.gamma.value = originalGamma
                gammaModified = false
            }
            return
        }

        // Save original gamma and boost it
        if (!gammaModified) {
            originalGamma = client.options.gamma.value
            client.options.gamma.value = 5.0 // Bright enough to see in darkness
            gammaModified = true
        }

        // Render desaturation shader
        shouldRender = true
    }
}
