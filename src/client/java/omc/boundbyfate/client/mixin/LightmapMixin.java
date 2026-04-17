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

    @Inject(method = "update", at = @At("TAIL"))
    private void bbf_applyDarkvision(float delta, CallbackInfo ci) {
        boolean hasDarkvision = DarkvisionState.INSTANCE.getHasDarkvision();
        
        // Always log first call
        if (firstLog) {
            LOGGER.info("[BBF Lightmap] FIRST CALL - hasDarkvision={}, image={}", 
                hasDarkvision, (image != null ? "present" : "null"));
            firstLog = false;
        }
        
        if (!hasDarkvision || image == null) return;

        // Sample a few pixels to see what we're working with (only log once per second)
        if (System.currentTimeMillis() % 1000 < 20) {
            int sample00 = image.getColor(0, 0);
            int sample77 = image.getColor(7, 7);
            int sample1515 = image.getColor(15, 15);
            
            LOGGER.info("[BBF Lightmap] Samples BEFORE: [0,0]={}, [7,7]={}, [15,15]={}", 
                Integer.toHexString(sample00), Integer.toHexString(sample77), Integer.toHexString(sample1515));
        }

        // Modify lightmap - boost brightness for low light levels
        // We need to make DARK cells (0-7) look like BRIGHT cells (7-14 or 15)
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
                    
                    // Sample the color from the BRIGHT cell
                    // Use boostedLight for BOTH coordinates to get the right brightness
                    int boostedColor = image.getColor(boostedLight, boostedLight);
                    
                    // Set the boosted color to the DARK cell
                    image.setColor(blockLight, skyLight, boostedColor);
                }
            }
        }
        
        // Log after modification
        if (System.currentTimeMillis() % 1000 < 20) {
            int sample00 = image.getColor(0, 0);
            int sample77 = image.getColor(7, 7);
            
            LOGGER.info("[BBF Lightmap] Samples AFTER: [0,0]={}, [7,7]={}", 
                Integer.toHexString(sample00), Integer.toHexString(sample77));
        }
    }
}
