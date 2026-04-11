package omc.boundbyfate.api.stat

import kotlin.math.floor

/**
 * Immutable snapshot of a computed stat value.
 *
 * Represents the final value of a stat after applying all modifiers,
 * along with the D&D 5e modifier calculation.
 *
 * @property base Base value before modifiers
 * @property total Final value after applying all modifiers (clamped to stat's range)
 * @property dndModifier D&D 5e modifier: floor((total - 10) / 2)
 */
data class StatValue(
    val base: Int,
    val total: Int,
    val dndModifier: Int
) {
    companion object {
        /**
         * Computes a StatValue from a base value and list of modifiers.
         *
         * Formula:
         * - Apply all FLAT modifiers additively
         * - Apply last OVERRIDE modifier (if any) to replace base
         * - Clamp total to [definition.minValue, definition.maxValue]
         * - Calculate D&D modifier: floor((total - 10) / 2)
         *
         * @param base Base value of the stat
         * @param modifiers List of modifiers to apply
         * @param definition Stat definition for clamping
         * @return Computed StatValue
         */
        fun compute(
            base: Int,
            modifiers: List<StatModifier>,
            definition: StatDefinition
        ): StatValue {
            var effectiveBase = base
            var flatSum = 0
            
            // Apply modifiers
            for (modifier in modifiers) {
                when (modifier.type) {
                    ModifierType.FLAT -> flatSum += modifier.value
                    ModifierType.OVERRIDE -> effectiveBase = modifier.value
                }
            }
            
            // Calculate total and clamp
            val total = definition.clamp(effectiveBase + flatSum)
            
            // D&D 5e modifier formula
            val dndModifier = floor((total - 10) / 2.0).toInt()
            
            return StatValue(
                base = effectiveBase,
                total = total,
                dndModifier = dndModifier
            )
        }
    }
}
