package omc.boundbyfate.client.mixin;

import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import omc.boundbyfate.client.gui.CharacterScreen;
import omc.boundbyfate.client.gui.FeatureScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public class InventoryScreenMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void bbf_addButtons(CallbackInfo ci) {
        InventoryScreen self = (InventoryScreen) (Object) this;

        // Position buttons to the left of the inventory
        int x = (self.width - 176) / 2;
        int y = (self.height - 166) / 2;

        // Character sheet button (📜) - above inventory on the left
        self.addDrawableChild(ButtonWidget.builder(
            Text.literal("📜"),
            btn -> self.client.setScreen(new CharacterScreen())
        ).dimensions(x - 24, y, 20, 20).build());

        // Feature screen button (⚔) - below character button
        self.addDrawableChild(ButtonWidget.builder(
            Text.literal("⚔"),
            btn -> self.client.setScreen(new FeatureScreen())
        ).dimensions(x - 24, y + 22, 20, 20).build());
    }
}
