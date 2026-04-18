package omc.boundbyfate.client.render

import ladysnake.satin.api.event.ShaderEffectRenderCallback
import ladysnake.satin.api.managed.ManagedShaderEffect
import ladysnake.satin.api.managed.ShaderEffectManager
import net.minecraft.client.MinecraftClient
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.LightType
import omc.boundbyfate.client.state.DarkvisionState

object DarkvisionRenderer {

    private val SHADER_ID = Identifier("boundbyfate-core", "shaders/post/darkvision.json")
    private val shader: ManagedShaderEffect = ShaderEffectManager.getInstance().manage(SHADER_ID)

    var shouldRender = false
        private set

    fun register() {
        ShaderEffectRenderCallback.EVENT.register { tickDelta ->
            if (shouldRender) {
                try {
                    val client = MinecraftClient.getInstance()
                    val player = client.player
                    val world = client.world
                    if (player != null && world != null) {
                        val pos = BlockPos.ofFloored(player.x, player.eyeY, player.z)
                        val blockLight = world.getLightLevel(LightType.BLOCK, pos)
                        val skyLight = world.getLightLevel(LightType.SKY, pos)
                        val effectiveLight = maxOf(blockLight, skyLight)

                        shader.findUniform1f("PlayerLightLevel")?.set(effectiveLight.toFloat())
                        shader.findUniform1f("IsUnderwater")?.set(if (DarkvisionState.isUnderwater) 1.0f else 0.0f)
                    }
                } catch (e: Exception) { /* shader not loaded */ }
                shader.render(tickDelta)
            }
        }
    }

    fun tick(client: MinecraftClient) {
        val player = client.player
        val world = client.world

        if (world == null || player == null || !DarkvisionState.hasDarkvision) {
            shouldRender = false
            DarkvisionState.isUnderwater = false
            return
        }

        DarkvisionState.isUnderwater = player.isSubmergedInWater
        shouldRender = false // Shader disabled - causes black horizon and dark water issues
    }
}
