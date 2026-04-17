package omc.boundbyfate.client.render

import ladysnake.satin.api.event.ShaderEffectRenderCallback
import ladysnake.satin.api.managed.ManagedShaderEffect
import ladysnake.satin.api.managed.ShaderEffectManager
import net.minecraft.client.MinecraftClient
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.LightType
import omc.boundbyfate.client.state.DarkvisionState

/**
 * Manages the darkvision post-process shader using Satin API.
 *
 * Satin allows loading shaders from any mod namespace, fixing the
 * "Non [a-z0-9_.-] character in namespace" error from vanilla PostEffectProcessor.
 *
 * The shader is rendered via ShaderEffectRenderCallback — it fires after the world
 * is drawn but before the HUD. We only call render() when darkvision is active.
 *
 * Light levels:
 *   0-7   = darkness  → full grayscale effect
 *   8-14  = dim light → partial effect (fades out)
 *   15    = bright    → no effect
 */
object DarkvisionRenderer {

    private val SHADER_ID = Identifier("boundbyfate-core", "shaders/post/darkvision.json")

    private val shader: ManagedShaderEffect = ShaderEffectManager.getInstance().manage(SHADER_ID)

    // Current shader parameters — updated each tick, used during render
    private var currentStrength = 0f
    private var currentThreshold = 0f
    private var shouldRender = false

    fun register() {
        ShaderEffectRenderCallback.EVENT.register { tickDelta ->
            if (shouldRender) {
                try {
                    shader.findUniform1f("DarkvisionThreshold")?.set(currentThreshold)
                    shader.findUniform1f("DarkvisionStrength")?.set(currentStrength)
                } catch (e: Exception) { /* shader not yet loaded */ }
                shader.render(tickDelta)
            }
        }
    }

    fun tick(client: MinecraftClient) {
        val world = client.world
        val player = client.player

        if (world == null || player == null || !DarkvisionState.hasDarkvision) {
            shouldRender = false
            return
        }

        val pos = BlockPos.ofFloored(player.x, player.eyeY, player.z)
        val blockLight = world.getLightLevel(LightType.BLOCK, pos)
        val skyLight = world.getLightLevel(LightType.SKY, pos)
        val lightLevel = maxOf(blockLight, skyLight)

        if (lightLevel >= 15) {
            shouldRender = false
            return
        }

        shouldRender = true

        if (lightLevel <= 7) {
            currentStrength = 1.0f
            currentThreshold = 0.4f
        } else {
            val t = (lightLevel - 7) / 7.0f
            currentStrength = 1.0f - t
            currentThreshold = 0.4f * (1.0f - t)
        }
    }
}
