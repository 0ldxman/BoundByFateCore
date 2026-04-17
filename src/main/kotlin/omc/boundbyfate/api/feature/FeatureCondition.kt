package omc.boundbyfate.api.feature

import omc.boundbyfate.api.effect.BbfEffectContext

/**
 * A condition that must be met for a feature to fire.
 * Used in FeatureConditionRegistry for JSON-defined conditions.
 */
fun interface FeatureCondition {
    fun check(context: BbfEffectContext): Boolean
}
