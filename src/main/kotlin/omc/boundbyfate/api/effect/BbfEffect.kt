package omc.boundbyfate.api.effect

/**
 * Universal effect interface used by both Features and Abilities.
 *
 * Effects are atomic actions — damage, heal, apply status, grant ability, etc.
 * They are registered in [omc.boundbyfate.registry.BbfEffectRegistry] with a
 * factory that reads parameters from JSON.
 *
 * An effect can be:
 * - Instant: applied once (damage, heal, teleport)
 * - Persistent: applied via a status effect container (resistance, AC modifier)
 *
 * Example registration:
 * ```kotlin
 * BbfEffectRegistry.register(Identifier("boundbyfate-core", "heal")) { params ->
 *     HealEffect(
 *         diceCount = params.get("diceCount")?.asInt ?: 1,
 *         diceType = DiceType.valueOf(params.get("diceType")?.asString ?: "D8")
 *     )
 * }
 * ```
 */
interface BbfEffect {
    /**
     * Apply this effect using the given context.
     * @return true if the effect was applied successfully
     */
    fun apply(context: BbfEffectContext): Boolean

    /**
     * Check if this effect can be applied in the given context.
     * Called before apply(). Return false to skip this effect without failure.
     */
    fun canApply(context: BbfEffectContext): Boolean = true
}
