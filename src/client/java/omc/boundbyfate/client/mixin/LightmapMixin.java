package omc.boundbyfate.client.mixin;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import omc.boundbyfate.client.state.DarkvisionState;

/**
 * Boosts lightmap brightness for darkvision.
 *
 * Darkvision brightness rules:
 *   Light 0-7 (darkness): appears as light 7-14 (dim light range)
 *   Light 8-14 (dim):     appears as light 15 (full bright)
 *   Light 15:             unchanged
 */
@Mixin(LightmapTextureManager.class)
public class LightmapMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("bbf-lightmap");
    private static int bbf_logCounter = 0;

    @Inject(method = "update", at = @At("RETURN"))
    private void bbf_applyDarkvision(float delta, CallbackInfo ci) {
        boolean hasDarkvision = DarkvisionState.INSTANCE.getHasDarkvision();

        // Log every 200 calls to confirm the mixin is firing
        bbf_logCounter++;
        if (bbf_logCounter % 200 == 0) {
            LOGGER.info("[BBF Lightmap] update() called, hasDarkvision={}", hasDarkvision);
        }

        if (!hasDarkvision) return;

        NativeImage image = ((LightmapTextureManagerAccessor)(Object)this).bbf_getImage();

        if (image == null) {
            LOGGER.warn("[BBF Lightmap] image is null!");
            return;
        }

        // Log image dimensions once
        if (bbf_logCounter % 200 == 1) {
            LOGGER.info("[BBF Lightmap] image size: {}x{}", image.getWidth(), image.getHeight());
        }

        for (int blockLight = 0; blockLight < 16; blockLight++) {
            for (int skyLight = 0; skyLight < 16; skyLight++) {
                int color = image.getColor(blockLight, skyLight);

                int a = (color >> 24) & 0xFF;
                int b = (color >> 16) & 0xFF;
                int g = (color >> 8)  & 0xFF;
                int r = (color)       & 0xFF;

                int effectiveLight = Math.max(blockLight, skyLight);

                // Skip full brightness — no boost needed
                if (effectiveLight >= 15) continue;

                float fr = r / 255.0f;
                float fg = g / 255.0f;
                float fb = b / 255.0f;

                float targetBrightness;
                if (effectiveLight < 8) {
                    // Darkness: shift up by 7 levels
                    targetBrightness = (effectiveLight + 7.0f) / 15.0f;
                } else {
                    // Dim light: boost to full bright
                    targetBrightness = 1.0f;
                }

                float lum = fr * 0.2126f + fg * 0.7152f + fb * 0.0722f;

                if (lum < targetBrightness) {
                    if (lum < 0.001f) {
                        fr = targetBrightness;
                        fg = targetBrightness;
                        fb = targetBrightness;
                    } else {
                        float boost = Math.min(targetBrightness / lum, 30.0f);
                        fr = Math.min(fr * boost, 1.0f);
                        fg = Math.min(fg * boost, 1.0f);
                        fb = Math.min(fb * boost, 1.0f);
                    }
                }

                r = (int)(fr * 255);
                g = (int)(fg * 255);
                b = (int)(fb * 255);

                image.setColor(blockLight, skyLight, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
    }
}
