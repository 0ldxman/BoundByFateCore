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
 * Manages the darkvision visual effect.
 *
 * Two-layer approach:
 * 1. Night Vision status effect — applied client-side to make Minecraft render
 *    the world brightly (this is how vanilla handles mob vision effects).
 *    Refreshed every tick so it never expires.
 * 2. Darkvision post-process shader — desaturates dark pixels to grayscale
 *    while leaving bright pixels colorful.
 *
 * Light levels:
 *   0-7   = darkness  → Night Vision + full grayscale shader
 *   8-14  = dim light → Night Vision + partial grayscale shader
 *   15    = bright    → no effect
 */
object DarkvisionRenderer {

    private val SHADER_ID = Identifier("boundbyfate-core", "shaders/post/darkvision.json")
    private val shader: ManagedShaderEffect = ShaderEffectManager.getInstance().manage(SHADER_ID)

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

        // Apply Night Vision client-side to make Minecraft render the world brightly.
        // Duration 30 ticks, refreshed every tick — effectively permanent while active.
        // ambient=true, showParticles=false, showIcon=false — invisible to player.
        val currentNV = player.getStatusEffect(StatusEffects.NIGHT_VISION)
        if (currentNV == null || currentNV.duration < 10) {
            player.addStatusEffect(
                StatusEffectInstance(StatusEffects.NIGHT_VISION, 30, 0, true, false, false)
            )
        }

        shouldRender = true
        if (lightLevel <= 7) {
            // Darkness: full grayscale — Night Vision shows the world, shader makes it gray
            currentStrength = 1.0f
            currentThreshold = 0.5f
        } else {
            // Dim light (8-14): partial grayscale, fade out as light increases
            val t = (lightLevel - 7) / 7.0f
            currentStrength = 1.0f - t
            currentThreshold = 0.5f * (1.0f - t)
        }
    }
}
