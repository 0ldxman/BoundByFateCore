package omc.boundbyfate.client.mixin.accessor;

import net.minecraft.entity.LimbAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LimbAnimator.class)
public interface LimbAnimatorAccessor {
    @Accessor("pos")
    float bbf_getPos();

    @Accessor("pos")
    void bbf_setPos(float pos);

    @Accessor("speed")
    float bbf_getSpeed();

    @Accessor("speed")
    void bbf_setSpeed(float speed);

    @Accessor("prevSpeed")
    float bbf_getPrevSpeed();

    @Accessor("prevSpeed")
    void bbf_setPrevSpeed(float speed);
}
