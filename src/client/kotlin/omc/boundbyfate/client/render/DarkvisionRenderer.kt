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
 * LightmapMixin brightens dark areas (darkness → dim, dim → bright).
 * This shader applies grayscale desaturation based on player's current light level.
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
                    
                    // Pass player's current light level to shader for desaturation
                    val client = MinecraftClient.getInstance()
                    val player = client.player
                    val world = client.world
                    if (player != null && world != null) {
                        val pos = net.minecraft.util.math.BlockPos.ofFloored(player.x, player.eyeY, player.z)
                        val blockLight = world.getLightLevel(net.minecraft.world.LightType.BLOCK, pos)
                        val skyLight = world.getLightLevel(net.minecraft.world.LightType.SKY, pos)
                        val lightLevel = maxOf(blockLight, skyLight)
                        
                        // Pass light level (0-15) to shader
                        shader.findUniform1f("PlayerLightLevel")?.set(lightLevel.toFloat())
                        
                        // Log occasionally
                        if (System.currentTimeMillis() % 1000 < 50) {
                            org.slf4j.LoggerFactory.getLogger("bbf-darkvision")
                                .info("Light level: block={}, sky={}, effective={}", blockLight, skyLight, lightLevel)
                        }
                    }
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

        // Always render shader when darkvision is active
        // Desaturation is based on player's current light level
        shouldRender = true
    }
}
