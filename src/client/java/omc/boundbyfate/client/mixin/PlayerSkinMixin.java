package omc.boundbyfate.client.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import omc.boundbyfate.client.skin.ClientSkinManager;
import omc.boundbyfate.component.components.EntityAppearanceData;
import omc.boundbyfate.data.world.character.ModelType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Подменяет текстуру скина игрока если назначен кастомный скин.
 *
 * Перехватывает {@link AbstractClientPlayerEntity#getSkinTexture()} и
 * возвращает текстуру из {@link ClientSkinManager} если у игрока есть
 * компонент {@link EntityAppearanceData} с непустым skinId.
 *
 * Если скин не назначен или не загружен — возвращает стандартный Mojang скин.
 */
@Mixin(AbstractClientPlayerEntity.class)
public class PlayerSkinMixin {

    @Inject(
        method = "getSkinTexture",
        at = @At("RETURN"),
        cancellable = true
    )
    private void bbf_overrideSkinTexture(CallbackInfoReturnable<Identifier> cir) {
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;

        // Читаем компонент внешности
        EntityAppearanceData appearance = self.getAttached(EntityAppearanceData.TYPE);
        if (appearance == null) return;

        String skinId = appearance.getSkinId();
        if (skinId == null || skinId.isEmpty()) return;

        // Определяем тип модели для загрузки
        String modelTypeStr = appearance.getModelType();
        ModelType modelType = "alex".equalsIgnoreCase(modelTypeStr)
            ? ModelType.ALEX
            : ModelType.STEVE;

        // Убеждаемся что скин загружен из кеша
        ClientSkinManager.INSTANCE.ensureLoaded(skinId, modelType);

        Identifier customTexture = ClientSkinManager.INSTANCE.getTexture(skinId);
        if (customTexture == null) return;

        cir.setReturnValue(customTexture);
    }

    @Inject(
        method = "getModel",
        at = @At("RETURN"),
        cancellable = true
    )
    private void bbf_overrideModel(CallbackInfoReturnable<String> cir) {
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;

        EntityAppearanceData appearance = self.getAttached(EntityAppearanceData.TYPE);
        if (appearance == null) return;

        String skinId = appearance.getSkinId();
        if (skinId == null || skinId.isEmpty()) return;

        // Возвращаем тип модели из компонента
        cir.setReturnValue(appearance.getModelType());
    }
}
