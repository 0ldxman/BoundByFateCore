package omc.boundbyfate.client.mixin;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import omc.boundbyfate.client.state.DarkvisionState;

/**
 * Implements darkvision by modifying the lightmap texture after vanilla fills it.
 *
 * The lightmap is a 16x16 texture:
 *   X axis = block light level (0-15) — torches, lava, glowstone, etc.
 *   Y axis = sky light level (0-15)   — sunlight, moonlight
 *
 * Darkvision rules:
 * - Darkness (effective light 0-7):
 *     → Brightness raised to ~50-70% (dim light appearance)
 *     → Color desaturated toward gray (darkness = grayscale)
 *     → BUT block light sources keep their color (torch stays orange, etc.)
 * - Dim light (effective light 8-14):
 *     → Brightness raised to ~80-100% (bright light appearance)
 *     → Color preserved (dim light = full color)
 * - Bright light (15): no modification
 *
 * The "light source color preservation" trick:
 *   When block light is high (≥ 8), the pixel is near a light source.
 *   We reduce desaturation proportionally — light sources keep their warm color.
 *   This mimics the Pale Garden effect where light sources glow with color.
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

                    // NativeImage uses ABGR format
                    int a = (color >> 24) & 0xFF;
                    int b = (color >> 16) & 0xFF;
                    int g = (color >> 8)  & 0xFF;
                    int r = (color)       & 0xFF;

                    int effectiveLight = Math.max(blockLight, skyLight);

                    if (effectiveLight < 8) {
                        // DARKNESS (0-7): raise brightness + desaturate
                        // Target brightness: 0.5 at light=0, 0.7 at light=7
                        float targetBrightness = 0.5f + (effectiveLight / 7.0f) * 0.2f;

                        // Luminance of current pixel
                        float lum = (r * 0.2126f + g * 0.7152f + b * 0.0722f) / 255.0f;

                        // How much to desaturate: fully gray at light=0, less at light=7
                        // Block light source preservation: if blockLight is high, keep color
                        float blockLightFactor = blockLight / 15.0f; // 0.0 = no block light, 1.0 = full
                        float desatBase = 1.0f - (effectiveLight / 7.0f); // 1.0 at dark, 0.0 at light=7
                        // Reduce desaturation near block light sources (torches keep their color)
                        float desatAmount = desatBase * (1.0f - blockLightFactor * 0.8f);

                        // Compute target color: mix original with gray
                        float targetLum = Math.max(lum, targetBrightness);
                        float newR = lerp(r / 255.0f, targetLum, desatAmount);
                        float newG = lerp(g / 255.0f, targetLum, desatAmount);
                        float newB = lerp(b / 255.0f, targetLum, desatAmount);

                        // Boost brightness to target level
                        float currentLum = newR * 0.2126f + newG * 0.7152f + newB * 0.0722f;
                        if (currentLum < targetBrightness && currentLum > 0.001f) {
                            float boost = targetBrightness / currentLum;
                            newR = Math.min(newR * boost, 1.0f);
                            newG = Math.min(newG * boost, 1.0f);
                            newB = Math.min(newB * boost, 1.0f);
                        } else if (currentLum < targetBrightness) {
                            newR = targetBrightness;
                            newG = targetBrightness;
                            newB = targetBrightness;
                        }

                        r = (int)(newR * 255);
                        g = (int)(newG * 255);
                        b = (int)(newB * 255);

                    } else if (effectiveLight < 15) {
                        // DIM LIGHT (8-14): boost to bright-light level, keep color
                        // Target brightness: 0.8 at light=8, 1.0 at light=14
                        float t = (effectiveLight - 8) / 6.0f;
                        float targetBrightness = 0.8f + t * 0.2f;

                        float lum = (r * 0.2126f + g * 0.7152f + b * 0.0722f) / 255.0f;
                        if (lum < targetBrightness && lum > 0.001f) {
                            float boost = targetBrightness / lum;
                            boost = Math.min(boost, 4.0f);
                            r = Math.min((int)(r * boost), 255);
                            g = Math.min((int)(g * boost), 255);
                            b = Math.min((int)(b * boost), 255);
                        } else if (lum < targetBrightness) {
                            r = (int)(targetBrightness * 255);
                            g = (int)(targetBrightness * 255);
                            b = (int)(targetBrightness * 255);
                        }
                    }
                    // effectiveLight == 15: bright light, no modification

                    image.setColor(blockLight, skyLight, (a << 24) | (b << 16) | (g << 8) | r);
                }
            }
        } catch (Exception e) {
            // Silently ignore
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
