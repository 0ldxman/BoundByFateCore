package omc.boundbyfate.api.combat

import net.minecraft.util.Identifier

/**
 * A single bonus damage component on a weapon.
 * Stored as NBT on the ItemStack under key "BbfBonusDamage".
 *
 * @property dice Dice expression, e.g. "1d4", "2d6"
 * @property damageType BbF damage type identifier
 * @property condition Optional condition ID. Null = always applies.
 *   Supported conditions:
 *   - "undead"     → target is undead (zombie, skeleton, etc.)
 *   - "construct"  → target is a construct (iron golem, etc.)
 *   - null         → unconditional
 */
data class BonusDamageEntry(
    val dice: String,
    val damageType: Identifier,
    val condition: String? = null
) {
    /** Human-readable condition label for tooltips. */
    val conditionLabel: String? get() = when (condition) {
        "undead"    -> "vs Нежить"
        "construct" -> "vs Конструкты"
        else        -> condition?.let { "vs $it" }
    }
}
