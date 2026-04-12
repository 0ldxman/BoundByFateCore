package omc.boundbyfate.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import omc.boundbyfate.system.combat.AttackRollSystem;
import omc.boundbyfate.system.combat.AttackResult;
import omc.boundbyfate.system.combat.WeaponDamageSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class AttackRollMixin {

    @Shadow public int hurtTime;
    @Shadow public int maxHurtTime;

    private static final ThreadLocal<AttackResult> PENDING_RESULT = new ThreadLocal<>();

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void bbf_resolveAttackRoll(DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        if (!bbf_isDirectAttack(source)) return;
        if (!(source.getAttacker() instanceof LivingEntity attacker)) return;

        LivingEntity target = (LivingEntity) (Object) this;
        AttackResult result = AttackRollSystem.INSTANCE.resolve(attacker, target);

        if (!result.getHit()) {
            PENDING_RESULT.remove();
            bbf_applyMissReaction(attacker, target);
            ci.setReturnValue(false);
            return;
        }

        PENDING_RESULT.set(result);
    }

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true, index = 2)
    private float bbf_replaceDamage(float amount, DamageSource source) {
        if (!bbf_isDirectAttack(source)) return amount;
        if (!(source.getAttacker() instanceof LivingEntity attacker)) return amount;

        AttackResult result = PENDING_RESULT.get();
        if (result == null) return amount;

        return WeaponDamageSystem.INSTANCE.calculate(attacker, attacker.getMainHandStack(), result.getCritical());
    }

    private boolean bbf_isDirectAttack(DamageSource source) {
        return source.getAttacker() != null &&
               (source.isOf(DamageTypes.PLAYER_ATTACK) ||
                source.isOf(DamageTypes.MOB_ATTACK) ||
                source.isOf(DamageTypes.ARROW) ||
                source.isOf(DamageTypes.TRIDENT));
    }

    /**
     * Applies knockback and hurt reaction on a miss.
     * Target reacts as if hit but takes no damage.
     */
    private void bbf_applyMissReaction(LivingEntity attacker, LivingEntity target) {
        double dx = target.getX() - attacker.getX();
        double dz = target.getZ() - attacker.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 0) { dx /= dist; dz /= dist; }

        // Slight knockback (weaker than a real hit)
        target.takeKnockback(0.25, -dx, -dz);

        // Hurt animation and sound
        AttackRollMixin targetMixin = (AttackRollMixin) (Object) target;
        if (targetMixin.hurtTime <= 0) {
            targetMixin.hurtTime = 10;
            targetMixin.maxHurtTime = 10;
            target.playHurtSound(attacker.getWorld().getDamageSources().genericKill());
        }
    }
}
