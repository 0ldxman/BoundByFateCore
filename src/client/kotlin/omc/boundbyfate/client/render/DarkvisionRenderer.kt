package omc.boundbyfate.client.render

import ladysnake.satin.api.event.ShaderEffectRenderCallback
import ladysnake.satin.api.managed.ManagedShaderEffect
import ladysnake.satin.api.managed.ShaderEffectManager
import net.minecraft.client.MinecraftClient
import net.minecraft.util.Identifier
import omc.boundbyfate.client.state.DarkvisionState

/**
 * Darkvision renderer using lightmap modification + desaturation shader.
 *
 * LightmapMixin brightens dark areas (like vanilla night vision).
 * This shader applies grayscale desaturation to dark pixels.
 */
object DarkvisionRenderer {

    private val SHADER_ID = Identifier("boundbyfate-core", "shaders/post/darkvision.json")
    private val shader: ManagedShaderEffect = ShaderEffectManager.getInstance().manage(SHADER_ID)

    var shouldRender = false
        private set

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
            return
        }

        // Only render shader in dark/dim conditions
        // In bright daylight (light 15), no desaturation needed
        val pos = net.minecraft.util.math.BlockPos.ofFloored(player.x, player.eyeY, player.z)
        val blockLight = world.getLightLevel(net.minecraft.world.LightType.BLOCK, pos)
        val skyLight = world.getLightLevel(net.minecraft.world.LightType.SKY, pos)
        val lightLevel = maxOf(blockLight, skyLight)

        // Render shader only when not in full bright light
        shouldRender = lightLevel < 15
    }
}
