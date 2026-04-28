package omc.boundbyfate.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import omc.boundbyfate.client.render.NpcModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Перехватывает рендер сущностей для отрисовки GLTF моделей НПС.
 *
 * Если у сущности есть компонент NpcModelComponent — рисуем GLTF модель
 * и отменяем стандартный рендер (EmptyEntityRenderer ничего не рисует,
 * но этот mixin нужен для LivingEntityRenderer который рисует броню и т.д.)
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity> {

    @Inject(
        method = "render",
        at = @At("HEAD"),
        cancellable = true
    )
    private void bbf_onRenderPre(
        T entity,
        float entityYaw,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        CallbackInfo ci
    ) {
        if (NpcModelRenderer.INSTANCE.onRenderPre(entity, entityYaw, partialTick, poseStack, buffer, packedLight)) {
            ci.cancel();
        }
    }
}
