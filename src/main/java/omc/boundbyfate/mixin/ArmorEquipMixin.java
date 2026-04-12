package omc.boundbyfate.mixin;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import omc.boundbyfate.system.combat.ArmorClassSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class ArmorEquipMixin {

    /**
     * Recalculates AC whenever equipment changes.
     * Covers equipping, unequipping, and item swaps in armor slots.
     */
    @Inject(method = "equipStack", at = @At("TAIL"))
    private void bbf_onEquipStack(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        // Only recalculate for armor/offhand slots
        if (slot == EquipmentSlot.MAINHAND) return;

        ArmorClassSystem.INSTANCE.recalculate(self);
    }
}
