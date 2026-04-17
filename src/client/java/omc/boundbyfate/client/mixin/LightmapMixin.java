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
 * Darkvision rules applied per lightmap texel:
 *
 * Darkness (effective light 0-7):
 *   → Brightness raised to ~65-80% (dim light appearance)
 *   → Color fully desaturated to gray (no colors in darkness)
 *   → Exception: high block light (torch/lava) keeps its warm color
 *     because the light source itself is bright — it's not "dark"
 *
 * Dim light (8-14):
 *   → Brightness raised to ~80-100% (bright light appearance)
 *   → Color partially preserved, fading to full color at light=14
 *
 * Bright light (15): no modification
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
                        // DARKNESS (0-7):
                        // - Raise brightness to simulate dim light appearance
                        // - Fully desaturate to gray (darkness = no color)
                        // - Exception: if block light is the dominant source AND it's high,
                        //   the texel is near a light source — preserve some color
                        //   (a torch in darkness still glows orange)

                        float targetBrightness = 0.65f + (effectiveLight / 7.0f) * 0.15f;

                        float lum = (r * 0.2126f + g * 0.7152f + b * 0.0722f) / 255.0f;

                        // Desaturation: fully gray in darkness
                        // Only reduce desaturation if block light is the primary source
                        // AND it's significantly brighter than sky light (actual light source nearby)
                        float blockDominance = (blockLight > skyLight && blockLight >= 8)
                            ? (blockLight - skyLight) / 15.0f
                            : 0.0f;
                        float desatAmount = 1.0f - blockDominance * 0.7f;

                        // Apply desaturation
                        float newR = lerp(r / 255.0f, lum, desatAmount);
                        float newG = lerp(g / 255.0f, lum, desatAmount);
                        float newB = lerp(b / 255.0f, lum, desatAmount);

                        // Boost brightness to target
                        float currentLum = newR * 0.2126f + newG * 0.7152f + newB * 0.0722f;
                        if (currentLum < targetBrightness) {
                            float boost = (currentLum > 0.001f)
                                ? targetBrightness / currentLum
                                : targetBrightness / 0.001f;
                            boost = Math.min(boost, 8.0f);
                            newR = Math.min(newR * boost, 1.0f);
                            newG = Math.min(newG * boost, 1.0f);
                            newB = Math.min(newB * boost, 1.0f);
                        }

                        r = (int)(newR * 255);
                        g = (int)(newG * 255);
                        b = (int)(newB * 255);

                    } else if (effectiveLight < 15) {
                        // DIM LIGHT (8-14):
                        // - Boost brightness toward bright-light level
                        // - Partial desaturation: light=8 is slightly gray, light=14 is full color
                        float t = (effectiveLight - 8) / 6.0f; // 0.0 at light=8, 1.0 at light=14
                        float targetBrightness = 0.80f + t * 0.20f;

                        // Slight desaturation at dim light, fading to full color
                        float desatAmount = (1.0f - t) * 0.3f; // 0.3 at light=8, 0.0 at light=14

                        float lum = (r * 0.2126f + g * 0.7152f + b * 0.0722f) / 255.0f;

                        float newR = lerp(r / 255.0f, lum, desatAmount);
                        float newG = lerp(g / 255.0f, lum, desatAmount);
                        float newB = lerp(b / 255.0f, lum, desatAmount);

                        float currentLum = newR * 0.2126f + newG * 0.7152f + newB * 0.0722f;
                        if (currentLum < targetBrightness && currentLum > 0.001f) {
                            float boost = Math.min(targetBrightness / currentLum, 4.0f);
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
