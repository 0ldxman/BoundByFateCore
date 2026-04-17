package omc.boundbyfate.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import omc.boundbyfate.client.state.DarkvisionState;

/**
 * Applies the darkvision post-process shader when the player has darkvision
 * and is in dim light or darkness.
 *
 * The shader desaturates dark pixels while leaving bright pixels colorful,
 * creating the D&D darkvision effect: darkness appears as dim grayscale,
 * dim light appears as bright color.
 */
@Mixin(GameRenderer.class)
public class DarkvisionMixin {

    private static final Identifier DARKVISION_SHADER =
        new Identifier("boundbyfate-core", "shaders/post/darkvision.json");

    /** Tracks whether we loaded the darkvision shader (to know when to unload it) */
    private boolean bbf_darkvisionActive = false;

    @Shadow
    public PostEffectProcessor postProcessor;

    @Shadow
    public void loadPostProcessor(Identifier id) {}

    @Shadow
    public void disablePostProcessor() {}

    @Inject(method = "tick", at = @At("HEAD"))
    private void bbf_tickDarkvision(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        boolean hasDarkvision = DarkvisionState.INSTANCE.getHasDarkvision();

        if (!hasDarkvision) {
            // Remove our shader if we had it active
            if (bbf_darkvisionActive) {
                disablePostProcessor();
                bbf_darkvisionActive = false;
            }
            return;
        }

        // Check light level at player eye position
        BlockPos eyePos = BlockPos.ofFloored(
            client.player.getX(),
            client.player.getEyeY(),
            client.player.getZ()
        );
        int blockLight = client.world.getLightLevel(LightType.BLOCK, eyePos);
        int skyLight = client.world.getLightLevel(LightType.SKY, eyePos);
        int lightLevel = Math.max(blockLight, skyLight);

        // Darkvision is active in dim light (8-14) and darkness (0-7)
        // In bright light (15) it has no effect
        boolean shouldApply = lightLevel < 15;

        if (shouldApply && !bbf_darkvisionActive) {
            // Only load if no other post-processor is active (e.g. spectator mode)
            if (postProcessor == null) {
                loadPostProcessor(DARKVISION_SHADER);
                bbf_darkvisionActive = true;
            }
        } else if (!shouldApply && bbf_darkvisionActive) {
            disablePostProcessor();
            bbf_darkvisionActive = false;
        }

        // Update shader uniforms based on current light level
        if (bbf_darkvisionActive && postProcessor != null) {
            updateDarkvisionUniforms(lightLevel);
        }
    }

    /**
     * Updates the darkvision shader uniforms based on current light level.
     *
     * Light 0-7 (darkness): full grayscale effect, threshold = 0.4
     *   → dark pixels become gray, bright pixels stay colorful
     * Light 8-14 (dim light): reduced effect, threshold decreases toward 0
     *   → less desaturation, approaching normal vision
     */
    private void updateDarkvisionUniforms(int lightLevel) {
        if (postProcessor == null) return;

        try {
            // In darkness (0-7): full effect. In dim light (8-14): fade out.
            float strength;
            float threshold;

            if (lightLevel <= 7) {
                // Full darkvision: dark areas are grayscale
                strength = 1.0f;
                threshold = 0.4f;
            } else {
                // Dim light: gradually reduce effect as light increases
                float t = (lightLevel - 7) / 7.0f; // 0.0 at light=7, 1.0 at light=14
                strength = 1.0f - t;
                threshold = 0.4f * (1.0f - t);
            }

            // Update uniforms on the shader pass
            postProcessor.getPasses().forEach(pass -> {
                try {
                    var thresholdUniform = pass.getProgram().getUniformByName("DarkvisionThreshold");
                    var strengthUniform = pass.getProgram().getUniformByName("DarkvisionStrength");
                    if (thresholdUniform != null) thresholdUniform.set(threshold);
                    if (strengthUniform != null) strengthUniform.set(strength);
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }
}
