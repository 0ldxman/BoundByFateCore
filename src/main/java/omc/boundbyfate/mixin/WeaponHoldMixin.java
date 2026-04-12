package omc.boundbyfate.mixin;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import omc.boundbyfate.system.combat.WeaponPropertySystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class WeaponHoldMixin {

    /**
     * When main hand changes:
     * - Apply/remove REACH attribute modifier
     * - If TWO_HANDED weapon equipped and offhand is occupied → clear offhand and notify
     */
    @Inject(method = "equipStack", at = @At("TAIL"))
    private void bbf_onWeaponEquip(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;

        if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
            // Reapply reach modifier based on current main hand
            WeaponPropertySystem.INSTANCE.applyPassiveEffects(self, self.getMainHandStack());
        }

        // TWO_HANDED enforcement: clear offhand if two-handed weapon is now in main hand
        if (slot == EquipmentSlot.MAINHAND && WeaponPropertySystem.INSTANCE.isTwoHandedViolation(self)) {
            // Drop offhand item into inventory or on ground
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
