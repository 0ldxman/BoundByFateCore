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
 * Instead of modifying the texture AFTER it's generated, we modify
 * the brightness values DURING generation using @ModifyArg.
 */
@Mixin(LightmapTextureManager.class)
public class LightmapMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("bbf-lightmap");
    private static int logCounter = 0;
    
    // Track current coordinates being processed
    private static int currentBlockLight = 0;
    private static int currentSkyLight = 0;

    @Shadow
    private NativeImage image;

    /**
     * Intercept the setColor call and modify the color value to brighten dark areas.
     * This is called for EVERY cell in the lightmap, including caves.
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

        // Extract x and y from the setColor call context
        // We need to track which cell is being set
        // For now, let's just brighten ALL dark colors
        
        // Extract RGB components
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        
        // Calculate brightness
        int brightness = Math.max(Math.max(r, g), b);
        
        // If this is a dark color, brighten it
        if (brightness < 128) {
            // Boost brightness for darkvision
            // Увеличил с 2.0 до 2.5 для большей яркости
            // Под водой ещё больше (3.5x) потому что вода затемняет
            float boost = DarkvisionState.INSTANCE.isUnderwater() ? 3.5f : 2.5f;
            r = Math.min(255, (int)(r * boost));
            g = Math.min(255, (int)(g * boost));
            b = Math.min(255, (int)(b * boost));
            
            int newColor = (a << 24) | (r << 16) | (g << 8) | b;
            
            // Log occasionally
            if (logCounter++ % 1000 == 0) {
                LOGGER.info("[BBF Lightmap] ModifyArg - underwater={}, brightness={}, old={}, new={}", 
                    DarkvisionState.INSTANCE.isUnderwater(), brightness, 
                    Integer.toHexString(color), Integer.toHexString(newColor));
            }
            
            return newColor;
        }
        
        return color;
    }
}
