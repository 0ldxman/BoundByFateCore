package omc.boundbyfate.client.mixin.accessor;

import net.minecraft.client.gl.ShaderProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ShaderProgram.class)
public interface ShaderProgramAccessor {
    @Accessor("glRef")
    int bbf_getGlRef();
}
