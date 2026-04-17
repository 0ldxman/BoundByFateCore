package omc.boundbyfate.client.mixin;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import omc.boundbyfate.client.state.DarkvisionState;

/**
 * Modifies lightmap to brighten dark areas when darkvision is active.
 * 
 * This is the same approach vanilla Night Vision uses - modify the lightmap texture
 * before geometry is rendered, so blocks in darkness render as if they're in dim light.
 */
@Mixin(LightmapTextureManager.class)
public class LightmapMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("bbf-lightmap");
    private static boolean firstLog = true;

    @Shadow
    private NativeImage image;

    @Inject(method = "update", at = @At("RETURN"))
    private void bbf_applyDarkvision(float delta, CallbackInfo ci) {
        boolean hasDarkvision = DarkvisionState.INSTANCE.getHasDarkvision();
        
        // Always log first call
        if (firstLog) {
            LOGGER.info("[BBF Lightmap] FIRST CALL - hasDarkvision={}, image={}", 
                hasDarkvision, (image != null ? "present" : "null"));
            firstLog = false;
        }
        
        if (!hasDarkvision || image == null) return;

        // Log when modifying
        LOGGER.info("[BBF Lightmap] MODIFYING lightmap ({}x{})", 
            image.getWidth(), image.getHeight());

        // Modify lightmap similar to vanilla night vision
        // For each light level combination (blockLight × skyLight):
        for (int blockLight = 0; blockLight < 16; blockLight++) {
            for (int skyLight = 0; skyLight < 16; skyLight++) {
                int color = image.getColor(blockLight, skyLight);

                // Extract RGBA components (ABGR format in NativeImage)
                int a = (color >> 24) & 0xFF;
                int b = (color >> 16) & 0xFF;
                int g = (color >> 8)  & 0xFF;
                int r = (color)       & 0xFF;

                // Calculate effective light level
                int effectiveLight = Math.max(blockLight, skyLight);

                // Darkvision rules:
                // - Darkness (0-7): boost to appear as dim light (7-14)
                // - Dim light (8-14): boost to appear as bright (15)
                // - Bright (15): no change

                if (effectiveLight < 15) {
                    float fr = r / 255.0f;
                    float fg = g / 255.0f;
                    float fb = b / 255.0f;

                    // Target brightness: shift light level up by 7-8
                    float targetBrightness;
                    if (effectiveLight < 8) {
                        // Darkness → dim light
                        targetBrightness = (effectiveLight + 7.0f) / 15.0f;
                    } else {
                        // Dim → bright
                        targetBrightness = 1.0f;
                    }

                    // Calculate current luminance
                    float lum = fr * 0.2126f + fg * 0.7152f + fb * 0.0722f;

                    // Boost if below target
                    if (lum < targetBrightness) {
                        if (lum < 0.001f) {
                            // Pitch black: set to target gray
                            fr = fg = fb = targetBrightness;
                        } else {
                            // Boost proportionally
                            float boost = Math.min(targetBrightness / lum, 10.0f);
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
}
