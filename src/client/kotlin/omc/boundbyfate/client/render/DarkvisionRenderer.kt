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
 * This renderer passes the player's BLOCK light level to the shader for desaturation.
 *
 * We use BLOCK light only (not sky light) because:
 * - Sky light raw value is always 15 on the surface, even at night
 * - Minecraft multiplies sky light by a time-of-day factor inside the lightmap
 * - Block light directly represents artificial light sources (torches, etc.)
 * - At night on the surface: blockLight = 0, so desaturation kicks in correctly
 * - In a lit cave: blockLight = 7-15, so colors are preserved correctly
 */
object DarkvisionRenderer {

    private val SHADER_ID = Identifier("boundbyfate-core", "shaders/post/darkvision.json")
    private val shader: ManagedShaderEffect = ShaderEffectManager.getInstance().manage(SHADER_ID)

    var shouldRender = false
        private set

    // Block light at player position (0-15), used by shader for desaturation
    var playerBlockLight = 0
        private set

    fun register() {
        ShaderEffectRenderCallback.EVENT.register { tickDelta ->
            if (shouldRender) {
                try {
                    val rangeBlocks = DarkvisionState.rangeFt / 5.0f * 1.5f
                    shader.findUniform1f("DarkvisionRange")?.set(rangeBlocks)
                    shader.findUniform1f("PlayerLightLevel")?.set(playerBlockLight.toFloat())
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
            playerBlockLight = 0
            return
        }

        val pos = BlockPos.ofFloored(player.x, player.eyeY, player.z)
        // Use BLOCK light only — sky light raw value is always 15 even at night
        playerBlockLight = world.getLightLevel(LightType.BLOCK, pos)

        // Always render shader when darkvision is active
        // (shader handles range fade and desaturation)
        shouldRender = true

        // Force lightmap to refresh every tick so our mixin boost is always applied
        client.gameRenderer.lightmapTextureManager.update(0f)
    }
}
