package omc.boundbyfate.api.ability

import omc.boundbyfate.api.ability.component.ConditionComponent
import omc.boundbyfate.api.effect.BbfEffect
import omc.boundbyfate.api.effect.BbfEffectContext

/**
 * Wraps a BbfEffect with phase binding, conditions, and execution settings.
 *
 * Allows:
 * - Binding an effect to a specific ability phase
 * - Adding conditions for execution
 * - Configuring failure behavior
 * - Choosing per-target vs once-for-all execution
 */
data class EffectEntry(
    val effect: BbfEffect,
    val phase: AbilityPhase = AbilityPhase.APPLICATION,
    val conditions: List<ConditionComponent> = emptyList(),
    val executeOnEachTarget: Boolean = true,
    val stopOnFailure: Boolean = false
) {
    /**
     * Checks conditions and applies the effect.
     * @return true if the effect succeeded or should not stop execution
     */
    fun execute(context: BbfEffectContext): Boolean {
        // Check conditions
        for (condition in conditions) {
            if (!condition.check(context)) {
                return !stopOnFailure
            }
        }

        if (!effect.canApply(context)) {
            return !stopOnFailure
        }

        return if (executeOnEachTarget && context.hasTargets) {
            var allSuccess = true
            for (target in context.targets) {
                val targetContext = context.copy(targets = listOf(target))
                if (!effect.apply(targetContext)) {
                    allSuccess = false
                    if (stopOnFailure) break
                }
            }
            allSuccess || !stopOnFailure
        } else {
            effect.apply(context) || !stopOnFailure
        }
    }
}
