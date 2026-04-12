package omc.boundbyfate.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import omc.boundbyfate.system.combat.AttackRollSystem;
import omc.boundbyfate.system.combat.AttackResult;
import omc.boundbyfate.system.combat.WeaponDamageSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class AttackRollMixin {

    // Thread-local to pass crit state from @Inject to @ModifyVariable
    private static final ThreadLocal<AttackResult> PENDING_RESULT = new ThreadLocal<>();

    /**
     * Step 1: Resolve attack roll. Cancel on miss, store result for damage calculation.
     */
    @Inject(
        method = "damage",
        at = @At("HEAD"),
        cancellable = true
    )
    private void bbf_resolveAttackRoll(DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        if (!bbf_isDirectAttack(source)) return;
        if (!(source.getAttacker() instanceof LivingEntity attacker)) return;

        LivingEntity target = (LivingEntity) (Object) this;
        AttackResult result = AttackRollSystem.INSTANCE.resolve(attacker, target);

        if (!result.getHit()) {
            PENDING_RESULT.remove();
            ci.setReturnValue(false);
            return;
        }

        PENDING_RESULT.set(result);
    }

    /**
     * Step 2: Replace vanilla damage with our calculated D&D damage.
     * Runs after the attack roll check, only if hit.
     */
    @ModifyVariable(
        method = "damage",
        at = @At("HEAD"),
        argsOnly = true,
        index = 2
    )
    private float bbf_replaceDamage(float amount, DamageSource source) {
        if (!bbf_isDirectAttack(source)) return amount;
        if (!(source.getAttacker() instanceof LivingEntity attacker)) return amount;

        AttackResult result = PENDING_RESULT.get();
        if (result == null) return amount;

        return WeaponDamageSystem.INSTANCE.calculate(attacker, attacker.getMainHandStack(), result.getIsCritical());
    }

    private boolean bbf_isDirectAttack(DamageSource source) {
        return source.getAttacker() != null &&
               (source.isOf(DamageTypes.PLAYER_ATTACK) ||
                source.isOf(DamageTypes.MOB_ATTACK) ||
                source.isOf(DamageTypes.ARROW) ||
                source.isOf(DamageTypes.TRIDENT));
    }
}
