package omc.boundbyfate.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import omc.boundbyfate.system.combat.AttackRollSystem;
import omc.boundbyfate.system.combat.AttackResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class AttackRollMixin {

    /**
     * Intercepts incoming melee/projectile damage and resolves a D&D attack roll.
     * If the roll misses, cancels the damage entirely.
     * Runs BEFORE the existing resistance mixin so a miss skips all processing.
     */
    @Inject(
        method = "damage",
        at = @At("HEAD"),
        cancellable = true
    )
    private void bbf_resolveAttackRoll(DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        LivingEntity target = (LivingEntity) (Object) this;

        // Only apply attack rolls to direct entity attacks (melee/projectile)
        if (!bbf_isDirectAttack(source)) return;

        // Need an attacker entity to roll
        if (source.getAttacker() == null || !(source.getAttacker() instanceof LivingEntity)) return;

        LivingEntity attacker = (LivingEntity) source.getAttacker();

        AttackResult result = AttackRollSystem.INSTANCE.resolve(attacker, target);

        if (!result.getHit()) {
            // Miss — cancel damage entirely
            ci.setReturnValue(false);
        }
        // Hit — let damage proceed normally through the rest of the pipeline
    }

    private boolean bbf_isDirectAttack(DamageSource source) {
        // Player/mob melee or projectile attacks
        return source.getAttacker() != null &&
               (source.isOf(net.minecraft.entity.damage.DamageTypes.PLAYER_ATTACK) ||
                source.isOf(net.minecraft.entity.damage.DamageTypes.MOB_ATTACK) ||
                source.isOf(net.minecraft.entity.damage.DamageTypes.ARROW) ||
                source.isOf(net.minecraft.entity.damage.DamageTypes.TRIDENT));
    }
}
