package omc.boundbyfate.mixin;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import omc.boundbyfate.registry.WeaponRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class WeaponHoldMixin {

    @Inject(method = "equipStack", at = @At("TAIL"))
    private void bbf_onWeaponEquip(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;

        if (slot == EquipmentSlot.MAINHAND && WeaponRegistry.INSTANCE.isTwoHandedViolation(self)) {
            ItemStack offhandItem = self.getOffHandStack();
            self.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            if (!self.getInventory().insertStack(offhandItem)) {
                self.dropItem(offhandItem, false);
            }
            self.sendMessage(
                Text.literal("§eДвуручное оружие требует свободной левой руки."),
                true
            );
        }
    }
}
