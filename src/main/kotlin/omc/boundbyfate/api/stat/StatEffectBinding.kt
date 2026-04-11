package omc.boundbyfate.api.stat

import net.minecraft.util.Identifier

/**
 * Binds a StatEffect to a specific stat.
 *
 * Used in StatDefinition to declare which effects should be applied
 * when the stat value changes.
 *
 * @property effect The effect to apply
 * @property statId The stat this effect is bound to
 */
data class StatEffectBinding(
    val effect: StatEffect,
    val statId: Identifier
)
