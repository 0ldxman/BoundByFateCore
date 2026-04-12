package omc.boundbyfate.api.feature

/**
 * Atomic effect applied as part of a feature.
 *
 * Effects are registered in [omc.boundbyfate.registry.FeatureEffectRegistry]
 * with a factory that reads parameters from JSON.
 *
 * Each effect is applied to EACH target in context.targets.
 * For SELF targeting, targets contains only the caster.
 *
 * Example registration:
 * ```kotlin
 * FeatureEffectRegistry.register(Identifier("boundbyfate-core", "heal")) { params ->
 *     HealEffect(
 *         diceCount = params.get("diceCount")?.asInt ?: 1,
 *         diceType = DiceType.valueOf(params.get("diceType")?.asString ?: "D8")
 *     )
 * }
 * ```
 */
interface FeatureEffect {
    /**
     * Apply this effect to all targets in the context.
     * The effect is responsible for iterating context.targets if needed.
     */
    fun apply(context: FeatureContext)

    /**
     * Check if this effect can be applied in the given context.
     * Called before apply(). Return false to skip this effect.
     */
    fun canApply(context: FeatureContext): Boolean = true
}
