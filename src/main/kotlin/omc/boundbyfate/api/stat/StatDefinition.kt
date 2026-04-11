package omc.boundbyfate.api.stat

import net.minecraft.util.Identifier

/**
 * Immutable definition of a character/mob stat (e.g., Strength, Dexterity).
 * 
 * StatDefinitions are registered in [omc.boundbyfate.registry.StatRegistry] and describe
 * the properties of a stat without storing actual values.
 *
 * @property id Unique identifier for this stat (e.g., "boundbyfate-core:strength")
 * @property shortName Short display name (e.g., "STR")
 * @property displayName Full display name (e.g., "Сила")
 * @property minValue Minimum allowed value (default: 1)
 * @property maxValue Maximum allowed value (default: 30)
 * @property defaultValue Default value when not specified (default: 10)
 *
 * @throws IllegalArgumentException if minValue > maxValue or defaultValue is out of range
 */
data class StatDefinition(
    val id: Identifier,
    val shortName: String,
    val displayName: String,
    val minValue: Int = 1,
    val maxValue: Int = 30,
    val defaultValue: Int = 10
) {
    init {
        require(minValue <= maxValue) {
            "StatDefinition $id: minValue ($minValue) must be <= maxValue ($maxValue)"
        }
        require(defaultValue in minValue..maxValue) {
            "StatDefinition $id: defaultValue ($defaultValue) must be in range [$minValue, $maxValue]"
        }
        require(shortName.isNotBlank()) {
            "StatDefinition $id: shortName cannot be blank"
        }
        require(displayName.isNotBlank()) {
            "StatDefinition $id: displayName cannot be blank"
        }
    }
    
    /**
     * Clamps a value to this stat's valid range.
     */
    fun clamp(value: Int): Int = value.coerceIn(minValue, maxValue)
}
