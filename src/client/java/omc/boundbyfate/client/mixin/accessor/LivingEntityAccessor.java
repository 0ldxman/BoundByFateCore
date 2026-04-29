package omc.boundbyfate.client.mixin.accessor;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("lastDamageTaken")
    float bbf_getLastDamageTaken();

    /** yarn: prevStepBobbingAmount → lastStrideDistance */
    @Accessor("lastStrideDistance")
    float bbf_getLastStrideDistance();

    /** yarn: stepBobbingAmount → strideDistance */
    @Accessor("strideDistance")
    float bbf_getStrideDistance();
}
