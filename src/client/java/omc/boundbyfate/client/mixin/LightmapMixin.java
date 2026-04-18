package omc.boundbyfate.client.mixin;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import omc.boundbyfate.client.state.DarkvisionState;

/**
 * Modifies lightmap brightness calculation for darkvision.
 * 
 * Uses @ModifyArg to intercept setColor calls and brighten dark colors
 * based on D&D 5e darkvision rules with gradation.
 */
@Mixin(LightmapTextureManager.class)
public class LightmapMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("bbf-lightmap");
    private static int logCounter = 0;

    @Shadow
    private NativeImage image;

    /**
     * Intercept the setColor call and modify the color value to brighten dark areas.
     * 
     * D&D Darkvision gradation:
     * - effectiveLight 0-6 → boost with gradation (darker areas get more boost)
     * - effectiveLight 7+ → moderate boost (already somewhat lit)
     */
    @ModifyArg(
        method = "update",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/texture/NativeImage;setColor(III)V"
        ),
        index = 2
    )
    private int bbf_modifyLightmapColor(int color) {
        boolean hasDarkvision = DarkvisionState.INSTANCE.getHasDarkvision();
        
        if (!hasDarkvision) {
            return color;
        }

        // Extract RGB components
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        
        // Calculate brightness (0-255)
        int brightness = Math.max(Math.max(r, g), b);
        
        // Apply darkvision boost based on brightness
        // Lower brightness = more boost needed
        float boost;
        if (brightness < 32) {
            // Very dark (0-31) → strong boost (3.5x)
            boost = 3.5f;
        } else if (brightness < 64) {
            // Dark (32-63) → good boost (3.0x)
            boost = 3.0f;
        } else if (brightness < 96) {
            // Dim (64-95) → moderate boost (2.5x)
            boost = 2.5f;
        } else if (brightness < 128) {
            // Somewhat dim (96-127) → light boost (2.0x)
            boost = 2.0f;
        } else {
            // Already bright (128+) → minimal boost (1.5x)
            boost = 1.0f;
        }
        
        // Apply boost
        r = Math.min(255, (int)(r * boost));
        g = Math.min(255, (int)(g * boost));
        b = Math.min(255, (int)(b * boost));
        
        int newColor = (a << 24) | (r << 16) | (g << 8) | b;
        
        // Logging disabled to reduce log spam
        // if (logCounter++ % 1000 == 0) {
        //     LOGGER.info("[BBF Lightmap] ModifyArg - brightness={}, boost={}, old={}, new={}", 
        //         brightness, boost, Integer.toHexString(color), Integer.toHexString(newColor));
        // }
        
        return newColor;
    }
}
