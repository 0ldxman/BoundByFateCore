package omc.boundbyfate.api.feature

/**
 * A condition that must be met for a feature to activate.
 *
 * Conditions are registered in [omc.boundbyfate.registry.FeatureConditionRegistry]
 * with a factory that reads parameters from JSON.
 *
 * Example registration:
 * ```kotlin
 * FeatureConditionRegistry.register(Identifier("boundbyfate-core", "health_below")) { params ->
 *     val threshold = params.get("threshold")?.asFloat ?: 0.5f
 *     FeatureCondition { ctx ->
 *         ctx.caster.health / ctx.caster.maxHealth < threshold
 *     }
 * }
 * ```
 */
fun interface FeatureCondition {
    /**
     * Returns true if the condition is met and the feature can proceed.
     */
    fun check(context: FeatureContext): Boolean
}
