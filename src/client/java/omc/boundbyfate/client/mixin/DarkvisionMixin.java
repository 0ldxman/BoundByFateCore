package omc.boundbyfate.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import omc.boundbyfate.client.state.DarkvisionState;

/**
 * Mixin to apply darkvision visual effects.
 *
 * Darkvision rules:
 * - Light level 0-7 (darkness): grayscale + brightness boost (see as dim light)
 * - Light level 8-14 (dim light): normal color + brightness boost (see as bright light)
 * - Light level 15 (bright light): no effect
 *
 * The grayscale effect is achieved by loading Minecraft's built-in
 * "desaturate" post-process shader when in darkness.
 */
@Mixin(GameRenderer.class)
public class DarkvisionMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void bbf_tickDarkvision(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        if (!DarkvisionState.INSTANCE.getHasDarkvision()) return;

        // Check distance — darkvision only works within range
        // (for simplicity we apply it globally when player has darkvision;
        //  range limiting would require per-block checks which is expensive)

        BlockPos playerPos = client.player.getBlockPos();
        int blockLight = client.world.getLightLevel(LightType.BLOCK, playerPos);
        int skyLight = client.world.getLightLevel(LightType.SKY, playerPos);
        int lightLevel = Math.max(blockLight, skyLight);

        boolean inDarkness = lightLevel <= 7;
        boolean inDimLight = lightLevel >= 8 && lightLevel <= 14;

        if (inDarkness) {
            // Apply grayscale shader if not already active
            if (client.gameRenderer.getPostProcessor() == null) {
                client.gameRenderer.loadPostProcessor(
                    net.minecraft.util.Identifier.of("minecraft", "shaders/post/desaturate.json")
                );
            }
        } else {
            // Remove grayscale shader if we applied it
            if (client.gameRenderer.getPostProcessor() != null) {
                // Only remove if we applied it (check by name)
                String name = client.gameRenderer.getPostProcessor().toString();
                if (name != null && name.contains("desaturate")) {
                    client.gameRenderer.disablePostProcessor();
                }
            }
        }
    }
}
