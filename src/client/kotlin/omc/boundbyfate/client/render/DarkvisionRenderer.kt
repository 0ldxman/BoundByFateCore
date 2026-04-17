package omc.boundbyfate.client.render

import net.minecraft.client.MinecraftClient
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.util.math.BlockPos
import net.minecraft.world.LightType
import omc.boundbyfate.client.state.DarkvisionState

/**
 * Manages the visual darkvision effect on the client.
 *
 * Called every client tick. Applies:
 * - Night Vision (amplifier 0, no particles) when in dim light or darkness
 *   to simulate "seeing dim light as bright" and "darkness as dim light"
 * - Grayscale post-process shader when in darkness (via DarkvisionMixin)
 *
 * Light levels:
 *   0-7   = darkness  → apply night vision + grayscale
 *   8-14  = dim light → apply night vision (no grayscale)
 *   15    = bright    → no effect
 */
object DarkvisionRenderer {

    fun tick(client: MinecraftClient) {
        val world = client.world ?: return
        val player = client.player ?: return
        if (!DarkvisionState.hasDarkvision) return

        val pos = BlockPos.ofFloored(player.x, player.eyeY, player.z)
        val blockLight = world.getLightLevel(LightType.BLOCK, pos)
        val skyLight = world.getLightLevel(LightType.SKY, pos)
        val lightLevel = maxOf(blockLight, skyLight)

        val inDarkness = lightLevel <= 7
        val inDimLight = lightLevel in 8..14
        val needsNightVision = inDarkness || inDimLight

        if (needsNightVision) {
            // Apply/refresh Night Vision with 2 ticks duration (refreshed every tick)
            // amplifier 0 = standard night vision brightness
            val currentEffect = player.getStatusEffect(StatusEffects.NIGHT_VISION)
            if (currentEffect == null || currentEffect.duration < 10) {
                player.addStatusEffect(
                    StatusEffectInstance(
                        StatusEffects.NIGHT_VISION,
                        20,   // 1 second, refreshed every tick
                        0,    // amplifier 0
                        true, // ambient (reduces particles)
                        false, // no particles
                        false  // no icon
                    )
                )
            }
        }
        // Grayscale in darkness is handled by DarkvisionMixin on GameRenderer.tick()
    }
}
