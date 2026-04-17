package omc.boundbyfate.client.mixin;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import omc.boundbyfate.client.state.DarkvisionState;

/**
 * Boosts lightmap brightness for darkvision.
 *
 * Uses @Shadow to access the NativeImage directly (avoids reflection + remapping issues).
 *
 * Rules:
 *   - Light 0-7 (darkness): appears as light 7-14 (dim light range)
 *   - Light 8-14 (dim): appears as light 15 (full bright)
 *   - Light 15: unchanged
 *
 * Grayscale/desaturation is handled by the post-process shader (darkvision.fsh)
 * which uses the player's actual light level uniform, not pixel brightness.
 */
@Mixin(LightmapTextureManager.class)
public class LightmapMixin {

    @Shadow
    private NativeImage image;

    @Inject(method = "update", at = @At("RETURN"))
    private void bbf_applyDarkvision(float delta, CallbackInfo ci) {
        if (!DarkvisionState.INSTANCE.getHasDarkvision()) return;
        if (image == null) return;

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

                // Target brightness based on darkvision rules:
                //   light 0 → target light 7  (0.467)
                //   light 7 → target light 14 (0.933)
                //   light 8 → target light 15 (1.0)
                //   light 14 → target light 15 (1.0)
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
                        // Pitch black pixel: set to neutral gray at target brightness
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
