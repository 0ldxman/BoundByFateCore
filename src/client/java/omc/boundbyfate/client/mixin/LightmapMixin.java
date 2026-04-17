package omc.boundbyfate.client.mixin;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import omc.boundbyfate.client.state.DarkvisionState;

/**
 * Boosts lightmap brightness for darkvision.
 *
 * The lightmap controls how bright each block appears based on light level.
 * We boost dark areas so they appear brighter (dim light appearance in darkness,
 * bright light appearance in dim light).
 *
 * Grayscale/desaturation is handled by the post-process shader (darkvision.fsh)
 * which operates on the final rendered image and can correctly identify
 * dark vs bright pixels.
 */
@Mixin(LightmapTextureManager.class)
public class LightmapMixin {

    @Inject(method = "update", at = @At("RETURN"))
    private void bbf_applyDarkvision(float delta, CallbackInfo ci) {
        if (!DarkvisionState.INSTANCE.getHasDarkvision()) return;

        try {
            var field = LightmapTextureManager.class.getDeclaredField("image");
            field.setAccessible(true);
            NativeImage image = (NativeImage) field.get(this);
            if (image == null) return;

            for (int blockLight = 0; blockLight < 16; blockLight++) {
                for (int skyLight = 0; skyLight < 16; skyLight++) {
                    int color = image.getColor(blockLight, skyLight);

                    int a = (color >> 24) & 0xFF;
                    int b = (color >> 16) & 0xFF;
                    int g = (color >> 8)  & 0xFF;
                    int r = (color)       & 0xFF;

                    int effectiveLight = Math.max(blockLight, skyLight);

                    float newR = r / 255.0f;
                    float newG = g / 255.0f;
                    float newB = b / 255.0f;

                    if (effectiveLight < 8) {
                        // DARKNESS (0-7): boost brightness to dim-light level
                        float targetBrightness = 0.65f + (effectiveLight / 7.0f) * 0.15f;
                        float lum = newR * 0.2126f + newG * 0.7152f + newB * 0.0722f;
                        if (lum < targetBrightness) {
                            float boost = (lum > 0.001f) ? Math.min(targetBrightness / lum, 8.0f) : 8.0f;
                            newR = Math.min(newR * boost, 1.0f);
                            newG = Math.min(newG * boost, 1.0f);
                            newB = Math.min(newB * boost, 1.0f);
                        }
                    } else if (effectiveLight < 15) {
                        // DIM LIGHT (8-14): boost brightness to bright-light level
                        float t = (effectiveLight - 8) / 6.0f;
                        float targetBrightness = 0.80f + t * 0.20f;
                        float lum = newR * 0.2126f + newG * 0.7152f + newB * 0.0722f;
                        if (lum < targetBrightness && lum > 0.001f) {
                            float boost = Math.min(targetBrightness / lum, 4.0f);
                            newR = Math.min(newR * boost, 1.0f);
                            newG = Math.min(newG * boost, 1.0f);
                            newB = Math.min(newB * boost, 1.0f);
                        }
                    }

                    r = (int)(newR * 255);
                    g = (int)(newG * 255);
                    b = (int)(newB * 255);

                    image.setColor(blockLight, skyLight, (a << 24) | (b << 16) | (g << 8) | r);
                }
            }
        } catch (Exception e) {
            // Silently ignore
        }
    }
}
