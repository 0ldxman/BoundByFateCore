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

    @Inject(method = "update", at = @At("HEAD"))
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
        LOGGER.info("[BBF Lightmap] MODIFYING lightmap at HEAD ({}x{})", 
            image.getWidth(), image.getHeight());

        // Modify lightmap - boost brightness for low light levels
        // This makes dark areas (light 0-7) appear as dim light (light 7-14)
        // and dim areas (light 8-14) appear as bright (light 15)
        for (int skyLight = 0; skyLight < 16; skyLight++) {
            for (int blockLight = 0; blockLight < 16; blockLight++) {
                int effectiveLight = Math.max(blockLight, skyLight);
                
                // Only boost if not already bright
                if (effectiveLight < 15) {
                    // Calculate boosted light level
                    int boostedLight;
                    if (effectiveLight < 8) {
                        // Darkness (0-7) → dim light (7-14)
                        boostedLight = effectiveLight + 7;
                    } else {
                        // Dim (8-14) → bright (15)
                        boostedLight = 15;
                    }
                    
                    // Sample the color from the boosted light level
                    // Use the higher of block/sky for both coordinates to get max brightness
                    int sampleX = Math.min(boostedLight, 15);
                    int sampleY = Math.min(boostedLight, 15);
                    int boostedColor = image.getColor(sampleX, sampleY);
                    
                    // Set the boosted color
                    image.setColor(blockLight, skyLight, boostedColor);
                }
            }
        }
    }
}
