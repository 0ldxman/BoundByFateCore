package omc.boundbyfate.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.util.Identifier;
import omc.boundbyfate.component.EntityDamageData;
import omc.boundbyfate.registry.BbfAttachments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public class LivingEntityDamageMixin {

    /**
     * Intercepts incoming damage and applies BoundByFate resistance modifiers.
     *
     * Runs before armor/enchantment calculations so the modifier affects raw damage.
     */
    @ModifyVariable(
        method = "damage",
        at = @At("HEAD"),
        argsOnly = true,
        index = 2
    )
    private float bbf_applyDamageResistance(float amount, DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;

        // Get damage type identifier
        RegistryEntry<DamageType> typeEntry = source.getTypeRegistryEntry();
        Identifier damageTypeId = typeEntry.getKey()
            .map(key -> key.getValue())
            .orElse(null);

        if (damageTypeId == null) return amount;

        // Check for resistance data
        EntityDamageData damageData = self.getAttachedOrElse(BbfAttachments.ENTITY_DAMAGE, null);
        if (damageData == null) return amount;

        float modifier = damageData.getModifier(damageTypeId);

        // Apply modifier
        return amount * modifier;
    }
}
