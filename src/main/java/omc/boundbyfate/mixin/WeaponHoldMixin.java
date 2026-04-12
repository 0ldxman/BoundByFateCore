package omc.boundbyfate.mixin;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import omc.boundbyfate.registry.WeaponRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class WeaponHoldMixin {

    /**
     * If TWO_HANDED weapon is equipped in main hand and offhand is occupied,
     * clear the offhand and notify the player.
     * Only applies to ServerPlayerEntity.
     */
    @Inject(method = "onEquipStack", at = @At("HEAD"))
    private void bbf_onWeaponEquip(EquipmentSlot slot, ItemStack oldStack, ItemStack newStack, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayerEntity player)) return;

        if (slot == EquipmentSlot.MAINHAND && WeaponRegistry.INSTANCE.isTwoHandedViolation(player)) {
            ItemStack offhandItem = player.getOffHandStack();
            player.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            if (!player.getInventory().insertStack(offhandItem)) {
                player.dropItem(offhandItem, false);
            }
            player.sendMessage(
                Text.literal("§eДвуручное оружие требует свободной левой руки."),
                true
            );
        }
    }
}
