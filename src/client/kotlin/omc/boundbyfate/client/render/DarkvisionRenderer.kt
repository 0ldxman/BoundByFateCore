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
 * LightmapMixin handles brightness boosting (dark areas appear brighter).
 * This renderer passes the player's actual light level to the shader so it can
 * correctly desaturate dark-world areas (not based on pixel brightness).
 */
object DarkvisionRenderer {

    private val SHADER_ID = Identifier("boundbyfate-core", "shaders/post/darkvision.json")
    private val shader: ManagedShaderEffect = ShaderEffectManager.getInstance().manage(SHADER_ID)

    private var shouldRender = false
    private var currentLightLevel = 0

    fun register() {
        ShaderEffectRenderCallback.EVENT.register { tickDelta ->
            if (shouldRender) {
                try {
                    val rangeBlocks = DarkvisionState.rangeFt / 5.0f * 1.5f
                    shader.findUniform1f("DarkvisionRange")?.set(rangeBlocks)
                    shader.findUniform1f("PlayerLightLevel")?.set(currentLightLevel.toFloat())
                } catch (e: Exception) { /* shader not yet loaded */ }
                shader.render(tickDelta)
            }
        }
    }

    fun tick(client: MinecraftClient) {
        val world = client.world
        val player = client.player

        if (world == null || player == null || !DarkvisionState.hasDarkvision) {
            if (shouldRender) {
                shouldRender = false
                // Force lightmap refresh so boost is removed
                client.gameRenderer.lightmapTextureManager.update(0f)
            }
            return
        }

        val pos = BlockPos.ofFloored(player.x, player.eyeY, player.z)
        val blockLight = world.getLightLevel(LightType.BLOCK, pos)
        val skyLight = world.getLightLevel(LightType.SKY, pos)
        val lightLevel = maxOf(blockLight, skyLight)

        currentLightLevel = lightLevel

        // Shader only needed when not in full bright light
        val newShouldRender = lightLevel < 15
        if (newShouldRender != shouldRender) {
            shouldRender = newShouldRender
            // Force lightmap refresh when state changes
            client.gameRenderer.lightmapTextureManager.update(0f)
        }
    }
}
