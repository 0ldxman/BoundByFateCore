package omc.boundbyfate.client.mixin;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import omc.boundbyfate.client.state.DarkvisionState;

/**
 * Mixin to implement darkvision by modifying the lightmap texture.
 *
 * The lightmap is a 16x16 texture where:
 *   X axis = sky light level (0-15)
 *   Y axis = block light level (0-15)
 *
 * For darkvision we modify the lightmap after vanilla fills it:
 * - Dark zones (low light): brightness is raised to simulate "dim light"
 * - Dim zones (medium light): brightness is raised to simulate "bright light"
 * - The color is also desaturated (shifted toward gray) for dark zones
 *
 * This is the same approach vanilla uses for Night Vision, but with
 * our custom gradation: darkness → dim light appearance (gray),
 * dim light → bright light appearance (color).
 */
@Mixin(LightmapTextureManager.class)
public class LightmapMixin {

    @Inject(method = "update", at = @At("RETURN"))
    private void bbf_applyDarkvision(float delta, CallbackInfo ci) {
        if (!DarkvisionState.INSTANCE.getHasDarkvision()) return;

        try {
            // Access the lightmap image via reflection
            var field = LightmapTextureManager.class.getDeclaredField("image");
            field.setAccessible(true);
            NativeImage image = (NativeImage) field.get(this);
            if (image == null) return;

            for (int blockLight = 0; blockLight < 16; blockLight++) {
                for (int skyLight = 0; skyLight < 16; skyLight++) {
                    int color = image.getColor(blockLight, skyLight);

                    // Extract ABGR components (NativeImage uses ABGR format)
                    int a = (color >> 24) & 0xFF;
                    int b = (color >> 16) & 0xFF;
                    int g = (color >> 8)  & 0xFF;
                    int r = (color)       & 0xFF;

                    // Effective light level for this texel
                    int effectiveLight = Math.max(blockLight, skyLight);

                    if (effectiveLight < 8) {
                        // DARKNESS (0-7): boost to dim-light level + desaturate to gray
                        // Map 0-7 → 0.3-0.6 brightness (dim light appearance)
                        float brightnessFactor = 0.3f + (effectiveLight / 7.0f) * 0.3f;

                        // Luminance for grayscale
                        float lum = (r * 0.2126f + g * 0.7152f + b * 0.0722f) / 255.0f;

                        // Boost luminance to target brightness
                        float targetLum = Math.max(lum, brightnessFactor);

                        // Desaturation: blend toward gray based on how dark it is
                        float desatAmount = 1.0f - (effectiveLight / 7.0f); // 1.0 at light=0, 0.0 at light=7

                        float newR = lerp(r / 255.0f, targetLum, desatAmount);
                        float newG = lerp(g / 255.0f, targetLum, desatAmount);
                        float newB = lerp(b / 255.0f, targetLum, desatAmount);

                        // Also boost overall brightness
                        newR = Math.min(newR + brightnessFactor * 0.5f, 1.0f);
                        newG = Math.min(newG + brightnessFactor * 0.5f, 1.0f);
                        newB = Math.min(newB + brightnessFactor * 0.5f, 1.0f);

                        r = (int)(newR * 255);
                        g = (int)(newG * 255);
                        b = (int)(newB * 255);

                    } else if (effectiveLight < 15) {
                        // DIM LIGHT (8-14): boost toward bright-light level, keep color
                        // Map 8-14 → 0.6-1.0 brightness
                        float t = (effectiveLight - 8) / 6.0f;
                        float brightnessFactor = 0.6f + t * 0.4f;

                        float lum = (r * 0.2126f + g * 0.7152f + b * 0.0722f) / 255.0f;
                        if (lum < brightnessFactor) {
                            float boost = brightnessFactor / Math.max(lum, 0.001f);
                            boost = Math.min(boost, 3.0f); // cap boost
                            r = Math.min((int)(r * boost), 255);
                            g = Math.min((int)(g * boost), 255);
                            b = Math.min((int)(b * boost), 255);
                        }
                    }
                    // effectiveLight == 15: bright light, no modification

                    int newColor = (a << 24) | (b << 16) | (g << 8) | r;
                    image.setColor(blockLight, skyLight, newColor);
                }
            }
        } catch (Exception e) {
            // Silently ignore — lightmap modification is best-effort
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
