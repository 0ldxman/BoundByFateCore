package omc.boundbyfate.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import omc.boundbyfate.network.ServerPacketHandler;
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

    @Shadow protected abstract void playHurtSound(DamageSource source);
    @Shadow protected abstract void onDamaged(DamageSource damageSource);

    private static final ThreadLocal<AttackResult> PENDING_RESULT = new ThreadLocal<>();

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void bbf_resolveAttackRoll(DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        if (!bbf_isDirectAttack(source)) return;
        if (!(source.getAttacker() instanceof LivingEntity attacker)) return;

        LivingEntity target = (LivingEntity) (Object) this;
        AttackResult result = AttackRollSystem.INSTANCE.resolve(attacker, target);

        // Send floating roll text to attacker if they're a player
        if (attacker instanceof ServerPlayerEntity player) {
            double displayY = target.getY() + target.getHeight() + 0.5;
            ServerPacketHandler.INSTANCE.sendAttackRoll(
                player,
                target.getX(), displayY, target.getZ(),
                result.getRoll(), result.getBonus(),
                result.getHit(), result.getCritical()
            );
        }

        if (!result.getHit()) {
            PENDING_RESULT.remove();
            bbf_applyMissReaction(attacker, target, source);
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
     * Applies knockback and mob AI response on a miss.
     * No hurt animation (no red flash) — visually the attack missed.
     */
    private void bbf_applyMissReaction(LivingEntity attacker, LivingEntity target, DamageSource source) {
        // Knockback only — no red flash
        double dx = target.getX() - attacker.getX();
        double dz = target.getZ() - attacker.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 0) { dx /= dist; dz /= dist; }
        target.takeKnockback(0.25, -dx, -dz);

        // Trigger AI response via onDamaged:
        // - passive mobs (sheep, cows) → FleeGoal activates
        // - neutral mobs (piglins, wolves) → become hostile
        // - hostile mobs → retarget attacker
        AttackRollMixin targetMixin = (AttackRollMixin) (Object) target;
        targetMixin.onDamaged(source);

        // For MobEntity: also set attacker as target if not already targeting
        if (target instanceof MobEntity mob && mob.getTarget() == null) {
            mob.setTarget(attacker);
        }
    }
}
