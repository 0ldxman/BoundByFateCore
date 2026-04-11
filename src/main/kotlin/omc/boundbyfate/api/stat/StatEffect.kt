package omc.boundbyfate.api.stat

import net.minecraft.entity.LivingEntity

/**
 * Functional interface for applying stat values to Minecraft entities.
 *
 * StatEffects translate computed stat values into actual game mechanics
 * (EntityAttributes, health, speed, status effects, etc.).
 *
 * Example usage:
 * ```kotlin
 * val damageEffect = StatEffect { entity, statValue ->
 *     val attribute = entity.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE)
 *     attribute?.addModifier(AttributeModifier(..., statValue.dndModifier.toDouble()))
 * }
 * ```
 */
fun interface StatEffect {
    /**
     * Applies the stat value to the entity.
     *
     * Called when:
     * - Entity stats are loaded/reloaded
     * - A stat modifier is added/removed
     * - Admin commands change stats
     *
     * @param entity The entity to apply the effect to
     * @param statValue The computed stat value with D&D modifier
     */
    fun apply(entity: LivingEntity, statValue: StatValue)
}
