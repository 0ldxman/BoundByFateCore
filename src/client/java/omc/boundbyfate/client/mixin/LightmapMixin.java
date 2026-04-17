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

                    // Darkvision brightness rules:
                    // - Darkness (0-7): appears as dim light (target brightness = light 7-14 range)
                    // - Dim light (8-14): appears as bright light (target brightness = 1.0)
                    //
                    // We set a minimum floor brightness so even pitch-black pixels get lit up.
                    // The shader handles grayscale desaturation separately.

                    if (effectiveLight < 8) {
                        // Map light 0→7, light 7→14 (shift up by 7)
                        // targetBrightness: light 0 = 7/15 ≈ 0.47, light 7 = 14/15 ≈ 0.93
                        float targetBrightness = (effectiveLight + 7.0f) / 15.0f;
                        float lum = newR * 0.2126f + newG * 0.7152f + newB * 0.0722f;
                        if (lum < targetBrightness) {
                            if (lum < 0.001f) {
                                // Pitch black: set to a neutral gray at target brightness
                                newR = targetBrightness;
                                newG = targetBrightness;
                                newB = targetBrightness;
                            } else {
                                float boost = Math.min(targetBrightness / lum, 30.0f);
                                newR = Math.min(newR * boost, 1.0f);
                                newG = Math.min(newG * boost, 1.0f);
                                newB = Math.min(newB * boost, 1.0f);
                            }
                        }
                    } else if (effectiveLight < 15) {
                        // Dim light (8-14): boost to full brightness
                        float targetBrightness = 1.0f;
                        float lum = newR * 0.2126f + newG * 0.7152f + newB * 0.0722f;
                        if (lum < targetBrightness) {
                            if (lum < 0.001f) {
                                newR = targetBrightness;
                                newG = targetBrightness;
                                newB = targetBrightness;
                            } else {
                                float boost = Math.min(targetBrightness / lum, 10.0f);
                                newR = Math.min(newR * boost, 1.0f);
                                newG = Math.min(newG * boost, 1.0f);
                                newB = Math.min(newB * boost, 1.0f);
                            }
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
