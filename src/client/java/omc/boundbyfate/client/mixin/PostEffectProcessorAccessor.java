package omc.boundbyfate.client.mixin;

import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Accessor mixin to expose the private 'passes' field of PostEffectProcessor.
 */
@Mixin(PostEffectProcessor.class)
public interface PostEffectProcessorAccessor {
    @Accessor("passes")
    List<PostEffectPass> bbf_getPasses();
}
