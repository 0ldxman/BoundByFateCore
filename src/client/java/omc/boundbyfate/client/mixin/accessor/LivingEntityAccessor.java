package omc.boundbyfate.client.mixin.accessor;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("lastDamageTaken")
    float bbf_getLastDamageTaken();

    /** yarn 1.20.1: prevStepBobbingAmount */
    @Accessor("prevStepBobbingAmount")
    float bbf_getLastStrideDistance();

    /** yarn 1.20.1: stepBobbingAmount */
    @Accessor("stepBobbingAmount")
    float bbf_getStrideDistance();
}
