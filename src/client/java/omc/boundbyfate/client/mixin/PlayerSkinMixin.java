package omc.boundbyfate.client.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import omc.boundbyfate.client.skin.ClientSkinManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public class PlayerSkinMixin {

    /**
     * Replaces the skin texture with our custom one if available.
     */
    @Inject(method = "getSkinTexture", at = @At("HEAD"), cancellable = true)
    private void bbf_getCustomSkin(CallbackInfoReturnable<Identifier> ci) {
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
        String name = self.getName().getString();

        ClientSkinManager.SkinEntry entry = ClientSkinManager.INSTANCE.getSkin(name);
        if (entry != null) {
            ci.setReturnValue(entry.getTextureId());
        }
    }

    /**
     * Returns the correct model type (slim/default) for the custom skin.
     */
    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void bbf_getSkinModel(CallbackInfoReturnable<String> ci) {
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
        String name = self.getName().getString();

        ClientSkinManager.SkinEntry entry = ClientSkinManager.INSTANCE.getSkin(name);
        if (entry != null) {
            ci.setReturnValue(entry.getSlim() ? "slim" : "default");
        }
    }
}
