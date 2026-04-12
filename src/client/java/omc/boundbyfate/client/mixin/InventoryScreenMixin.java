package omc.boundbyfate.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import omc.boundbyfate.client.gui.CharacterScreen;
import omc.boundbyfate.client.gui.FeatureScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends Screen {

    @Shadow
    protected MinecraftClient client;

    @Shadow
    protected abstract <T extends Element & Drawable & Selectable> T addDrawableChild(T drawableElement);

    protected InventoryScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void bbf_addButtons(CallbackInfo ci) {
        int x = (this.width - 176) / 2;
        int y = (this.height - 166) / 2;

        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("📜"),
            btn -> this.client.setScreen(new CharacterScreen())
        ).dimensions(x - 24, y, 20, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("⚔"),
            btn -> this.client.setScreen(new FeatureScreen())
        ).dimensions(x - 24, y + 22, 20, 20).build());
    }
}
