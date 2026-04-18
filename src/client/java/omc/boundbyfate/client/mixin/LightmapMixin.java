package omc.boundbyfate.client.mixin;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import omc.boundbyfate.client.state.DarkvisionState;

/**
 * Modifies lightmap to implement D&D 5e darkvision rules.
 * 
 * D&D Rules:
 * - Bright light (8-15) → stays bright (15)
 * - Dim light (1-7) → becomes bright (15)
 * - Darkness (0) → becomes dim (7)
 */
@Mixin(LightmapTextureManager.class)
public class LightmapMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("bbf-lightmap");
    private static int logCounter = 0;

    @Shadow
    private NativeImage image;

    /**
     * Modify the lightmap texture AFTER it's generated to apply darkvision.
     * We copy colors from brighter cells to darker cells based on D&D rules.
     */
    @Inject(method = "update", at = @At("RETURN"))
    private void bbf_applyDarkvision(float delta, CallbackInfo ci) {
        boolean hasDarkvision = DarkvisionState.INSTANCE.getHasDarkvision();
        
        if (!hasDarkvision || image == null) {
            return;
        }

        // D&D 5e Darkvision rules with gradation:
        // - Darkness/Dim (0-6) → Gradual boost from 5 to 14
        // - Dim/Bright (7+) → Full bright (15)
        
        // Process each cell in the 16x16 lightmap
        // X = block light (0-15), Y = sky light (0-15)
        for (int skyLight = 0; skyLight < 16; skyLight++) {
            for (int blockLight = 0; blockLight < 16; blockLight++) {
                // Effective light = max of block and sky
                int effectiveLight = Math.max(blockLight, skyLight);
                
                int sourceLight;
                if (effectiveLight < 7) {
                    // Darkness/Dim (0-6) → Gradual boost
                    // Linear interpolation: 0→5, 1→6.5, 2→8, 3→9.5, 4→11, 5→12.5, 6→14
                    // Formula: sourceLight = 5 + (effectiveLight * 9 / 6)
                    sourceLight = 5 + (effectiveLight * 9 / 6);
                } else {
                    // Dim/Bright (7+) → Full bright
                    sourceLight = 15;
                }
                
                // Copy color from the source light level
                // Use sourceLight for BOTH coordinates to get consistent brightness
                int boostedColor = image.getColor(sourceLight, sourceLight);
                image.setColor(blockLight, skyLight, boostedColor);
            }
        }
        
        // Log occasionally
        if (logCounter++ % 100 == 0) {
            int sample00 = image.getColor(0, 0);
            int sample77 = image.getColor(7, 7);
            int sample1515 = image.getColor(15, 15);
            LOGGER.info("[BBF Lightmap] Applied darkvision - [0,0]={}, [7,7]={}, [15,15]={}", 
                Integer.toHexString(sample00), Integer.toHexString(sample77), Integer.toHexString(sample1515));
        }
    }
}
