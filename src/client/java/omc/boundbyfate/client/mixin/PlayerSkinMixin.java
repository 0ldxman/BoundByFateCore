package omc.boundbyfate.client.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import omc.boundbyfate.client.skin.ClientSkinManager;
import omc.boundbyfate.component.components.EntityAppearanceData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Подменяет текстуру скина игрока если назначен кастомный скин.
 *
 * Перехватывает {@link AbstractClientPlayerEntity#getSkinTextures()} и
 * возвращает текстуру из {@link ClientSkinManager} если у игрока есть
 * компонент {@link EntityAppearanceData} с непустым skinId.
 *
 * Если скин не назначен или не загружен — возвращает стандартный Mojang скин.
 */
@Mixin(AbstractClientPlayerEntity.class)
public class PlayerSkinMixin {

    @Inject(
        method = "getSkinTextures",
        at = @At("RETURN"),
        cancellable = true
    )
    private void bbf_overrideSkinTextures(CallbackInfoReturnable<SkinTextures> cir) {
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;

        // Читаем компонент внешности
        EntityAppearanceData appearance = self.getAttached(EntityAppearanceData.TYPE);
        if (appearance == null) return;

        String skinId = appearance.getSkinId();
        if (skinId == null || skinId.isEmpty()) return;

        // Убеждаемся что скин загружен из кеша
        ClientSkinManager.INSTANCE.ensureLoaded(skinId);

        Identifier customTexture = ClientSkinManager.INSTANCE.getTexture(skinId);
        if (customTexture == null) return;

        // Определяем тип модели
        String modelTypeStr = appearance.getModelType();
        SkinTextures.Model model = "alex".equalsIgnoreCase(modelTypeStr)
            ? SkinTextures.Model.SLIM
            : SkinTextures.Model.WIDE;

        // Берём оригинальные SkinTextures и подменяем только текстуру и модель
        SkinTextures original = cir.getReturnValue();
        SkinTextures custom = new SkinTextures(
            customTexture,
            original.textureUrl(),
            original.capeTexture(),
            original.elytraTexture(),
            model,
            original.secure()
        );

        cir.setReturnValue(custom);
    }
}
