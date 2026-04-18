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
                        
                        // Get light levels using the correct method (from LightLevel mod reference)
                        val blockLight = world.getLightLevel(net.minecraft.world.LightType.BLOCK, pos)
                        val skyLight = world.getLightLevel(net.minecraft.world.LightType.SKY, pos)
                        
                        // Calculate effective light (max of block and sky)
                        val effectiveLight = maxOf(blockLight, skyLight)
                        
                        // IMPORTANT: Apply darkvision boost to the light level we pass to shader
                        // This way the shader knows the "perceived" light level, not the real one
                        val perceivedLight = if (effectiveLight < 8) {
                            // Darkness (0-7) → appears as dim light (7-14)
                            effectiveLight + 7
                        } else if (effectiveLight < 15) {
                            // Dim (8-14) → appears as bright (15)
                            15
                        } else {
                            // Already bright
                            15
                        }
                        
                        // Pass PERCEIVED light level to shader for desaturation
                        // Low perceived light = grayscale, high perceived light = color
                        shader.findUniform1f("PlayerLightLevel")?.set(perceivedLight.toFloat())
                        
                        // Log occasionally (every ~2 seconds)
                        if (System.currentTimeMillis() % 2000 < 50) {
                            org.slf4j.LoggerFactory.getLogger("bbf-darkvision")
                                .info("Light: real={}(block)+{}(sky)={}, perceived={}", 
                                    blockLight, skyLight, effectiveLight, perceivedLight)
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
            DarkvisionState.isUnderwater = false
            return
        }

        // Update underwater status
        DarkvisionState.isUnderwater = player.isSubmergedInWater

        // Always render shader when darkvision is active
        // Desaturation is based on player's current light level
        shouldRender = true
    }
}
