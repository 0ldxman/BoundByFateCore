package omc.boundbyfate.api.combat

import net.minecraft.entity.LivingEntity

/**
 * A condition that determines whether bonus damage should be applied.
 *
 * Conditions are registered in [omc.boundbyfate.registry.DamageConditionRegistry]
 * with a factory that reads parameters from NBT/JSON.
 *
 * Example registration:
 * ```kotlin
 * DamageConditionRegistry.register(Identifier("mymod", "is_boss")) { _ ->
 *     DamageCondition { _, target ->
 *         target.getAttachedOrElse(MyAttachments.BOSS_DATA, null) != null
 *     }
 * }
 * ```
 *
 * Example with parameters:
 * ```kotlin
 * DamageConditionRegistry.register(Identifier("boundbyfate-core", "target_has_status")) { params ->
 *     val statusId = Identifier(params.getString("statusId"))
 *     DamageCondition { _, target ->
 *         target.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, null)?.hasStatus(statusId) == true
 *     }
 * }
 * ```
 */
fun interface DamageCondition {
    /**
     * @param attacker The entity performing the attack
     * @param target The entity being attacked
     * @return true if the bonus damage should be applied
     */
    fun test(attacker: LivingEntity, target: LivingEntity): Boolean
}
