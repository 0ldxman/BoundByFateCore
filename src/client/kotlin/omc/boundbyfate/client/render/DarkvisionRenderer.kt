package omc.boundbyfate.client.render

import ladysnake.satin.api.event.ShaderEffectRenderCallback
import ladysnake.satin.api.managed.ManagedShaderEffect
import ladysnake.satin.api.managed.ShaderEffectManager
import net.minecraft.client.MinecraftClient
import net.minecraft.util.Identifier
import omc.boundbyfate.client.state.DarkvisionState
import org.slf4j.LoggerFactory

/**
 * Darkvision post-process shader renderer.
 *
 * Uses exposure boost (like HDR/night vision camera) to pull detail out of shadows,
 * then applies grayscale desaturation to dark areas.
 *
 * This approach works because Minecraft DOES render geometry in dark areas,
 * it's just very dim. Exposure boost amplifies that dim signal.
 */
object DarkvisionRenderer {

    private val LOGGER = LoggerFactory.getLogger("bbf-darkvision")
    private val SHADER_ID = Identifier("boundbyfate-core", "shaders/post/darkvision.json")
    private val shader: ManagedShaderEffect = ShaderEffectManager.getInstance().manage(SHADER_ID)

    var shouldRender = false
        private set

    private var logCounter = 0

    fun register() {
        LOGGER.info("Registering darkvision shader callback")
        ShaderEffectRenderCallback.EVENT.register { tickDelta ->
            if (shouldRender) {
                try {
                    val rangeBlocks = DarkvisionState.rangeFt / 5.0f * 1.5f
                    shader.findUniform1f("DarkvisionRange")?.set(rangeBlocks)
                    
                    // Log every 100 renders
                    logCounter++
                    if (logCounter % 100 == 0) {
                        LOGGER.info("Rendering darkvision shader (range: {} blocks)", rangeBlocks)
                    }
                } catch (e: Exception) {
                    LOGGER.error("Error setting shader uniforms", e)
                }
                shader.render(tickDelta)
            }
        }
    }

    fun tick(client: MinecraftClient) {
        val world = client.world
        val player = client.player

        val hadDarkvision = shouldRender
        
        if (world == null || player == null || !DarkvisionState.hasDarkvision) {
            shouldRender = false
            if (hadDarkvision) {
                LOGGER.info("Darkvision disabled (hasDarkvision={})", DarkvisionState.hasDarkvision)
            }
            return
        }

        // Always render shader when darkvision is active
        if (!shouldRender) {
            LOGGER.info("Darkvision enabled (rangeFt={})", DarkvisionState.rangeFt)
        }
        shouldRender = true
    }
}
