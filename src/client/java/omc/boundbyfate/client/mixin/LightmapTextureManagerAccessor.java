package omc.boundbyfate.client.mixin;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LightmapTextureManager.class)
public interface LightmapTextureManagerAccessor {
    @Accessor("image")
    NativeImage bbf_getImage();

    @Accessor("dirty")
    boolean bbf_isDirty();

    @Accessor("dirty")
    void bbf_setDirty(boolean dirty);
}
