package omc.boundbyfate.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectPass;
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

import java.util.List;

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

    private boolean bbf_darkvisionActive = false;

    @Shadow
    PostEffectProcessor postProcessor;

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
            if (bbf_darkvisionActive) {
                disablePostProcessor();
                bbf_darkvisionActive = false;
            }
            return;
        }

        BlockPos eyePos = BlockPos.ofFloored(
            client.player.getX(),
            client.player.getEyeY(),
            client.player.getZ()
        );
        int blockLight = client.world.getLightLevel(LightType.BLOCK, eyePos);
        int skyLight = client.world.getLightLevel(LightType.SKY, eyePos);
        int lightLevel = Math.max(blockLight, skyLight);

        boolean shouldApply = lightLevel < 15;

        if (shouldApply && !bbf_darkvisionActive) {
            if (postProcessor == null) {
                loadPostProcessor(DARKVISION_SHADER);
                bbf_darkvisionActive = true;
            }
        } else if (!shouldApply && bbf_darkvisionActive) {
            disablePostProcessor();
            bbf_darkvisionActive = false;
        }

        if (bbf_darkvisionActive && postProcessor != null) {
            updateDarkvisionUniforms(lightLevel);
        }
    }

    private void updateDarkvisionUniforms(int lightLevel) {
        if (postProcessor == null) return;

        float strength;
        float threshold;

        if (lightLevel <= 7) {
            strength = 1.0f;
            threshold = 0.4f;
        } else {
            float t = (lightLevel - 7) / 7.0f;
            strength = 1.0f - t;
            threshold = 0.4f * (1.0f - t);
        }

        // Access passes via the accessor mixin
        List<PostEffectPass> passes = ((PostEffectProcessorAccessor) postProcessor).bbf_getPasses();
        if (passes == null) return;

        for (PostEffectPass pass : passes) {
            try {
                var program = pass.getProgram();
                if (program == null) continue;

                var thresholdUniform = program.getUniformByName("DarkvisionThreshold");
                var strengthUniform = program.getUniformByName("DarkvisionStrength");

                if (thresholdUniform != null) thresholdUniform.set(threshold);
                if (strengthUniform != null) strengthUniform.set(strength);
            } catch (Exception ignored) {}
        }
    }
}
