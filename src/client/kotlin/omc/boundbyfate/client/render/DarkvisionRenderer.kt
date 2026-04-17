package omc.boundbyfate.client.render

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
 * Light levels:
 *   0-7   = darkness  → full grayscale effect
 *   8-14  = dim light → partial effect (fades out)
 *   15    = bright    → no effect
 */
object DarkvisionRenderer {

    private val SHADER_ID = Identifier("boundbyfate-core", "shaders/post/darkvision.json")

    private val shader: ManagedShaderEffect = ShaderEffectManager.getInstance().manage(SHADER_ID)

    private var wasActive = false

    fun tick(client: MinecraftClient) {
        val world = client.world ?: run { deactivate(); return }
        val player = client.player ?: run { deactivate(); return }

        if (!DarkvisionState.hasDarkvision) {
            deactivate()
            return
        }

        val pos = BlockPos.ofFloored(player.x, player.eyeY, player.z)
        val blockLight = world.getLightLevel(LightType.BLOCK, pos)
        val skyLight = world.getLightLevel(LightType.SKY, pos)
        val lightLevel = maxOf(blockLight, skyLight)

        if (lightLevel >= 15) {
            deactivate()
            return
        }

        // Calculate shader parameters based on light level
        val strength: Float
        val threshold: Float
        if (lightLevel <= 7) {
            strength = 1.0f
            threshold = 0.4f
        } else {
            val t = (lightLevel - 7) / 7.0f
            strength = 1.0f - t
            threshold = 0.4f * (1.0f - t)
        }

        // Enable shader and update uniforms
        if (!wasActive) {
            shader.enable()
            wasActive = true
        }

        try {
            shader.findUniform1f("DarkvisionThreshold")?.set(threshold)
            shader.findUniform1f("DarkvisionStrength")?.set(strength)
        } catch (e: Exception) {
            // Shader not yet loaded — will apply next tick
        }
    }

    private fun deactivate() {
        if (wasActive) {
            shader.disable()
            wasActive = false
        }
    }
}
