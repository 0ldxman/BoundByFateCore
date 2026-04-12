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

    private static final ThreadLocal<AttackResult> PENDING_RESULT = new ThreadLocal<>();
    // Flag to skip our mixin when we call damage() ourselves for AI triggering
    private static final ThreadLocal<Boolean> SKIP_ROLL = ThreadLocal.withInitial(() -> false);

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void bbf_resolveAttackRoll(DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        // Skip if we triggered this damage call ourselves
        if (SKIP_ROLL.get()) return;
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

        float mainDamage = WeaponDamageSystem.INSTANCE.calculate(attacker, attacker.getMainHandStack(), result.getCritical());

        // Schedule bonus damage after main damage is applied
        LivingEntity target = (LivingEntity) (Object) this;
        WeaponDamageSystem.INSTANCE.applyBonusDamage(attacker, attacker.getMainHandStack(), target, result.getCritical());

        return mainDamage;
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
     * Calls damage(0) with SKIP_ROLL flag so Minecraft processes AI normally
     * without triggering our attack roll again.
     */
    private void bbf_applyMissReaction(LivingEntity attacker, LivingEntity target, DamageSource source) {
        // Knockback — no red flash (no hurtTime set)
        double dx = target.getX() - attacker.getX();
        double dz = target.getZ() - attacker.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 0) { dx /= dist; dz /= dist; }
        target.takeKnockback(0.25, -dx, -dz);

        // Trigger full AI response by calling damage(0.001f) with our skip flag.
        // This lets Minecraft run its normal post-damage AI logic (flee, aggro)
        // without dealing actual damage or triggering our roll again.
        SKIP_ROLL.set(true);
        try {
            target.damage(source, 0.001f);
        } finally {
            SKIP_ROLL.set(false);
        }

        // For MobEntity: also explicitly set attacker as target
        if (target instanceof MobEntity mob && mob.getTarget() == null) {
            mob.setTarget(attacker);
        }
    }
}
