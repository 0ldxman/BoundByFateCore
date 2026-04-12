package omc.boundbyfate.api.combat

import com.google.gson.JsonObject
import net.minecraft.util.Identifier
import omc.boundbyfate.registry.DamageConditionRegistry

/**
 * A single bonus damage component on a weapon.
 * Stored as NBT on the ItemStack under key "BbfBonusDamage".
 *
 * NBT format:
 * ```
 * BbfBonusDamage: [
 *   {dice: "1d4", type: "boundbyfate-core:fire"},
 *   {dice: "1d6", type: "boundbyfate-core:radiant", condition: "boundbyfate-core:undead"},
 *   {dice: "1d8", type: "boundbyfate-core:necrotic",
 *    condition: "boundbyfate-core:target_has_status",
 *    conditionParams: {statusId: "boundbyfate-core:burning"}}
 * ]
 * ```
 *
 * @property dice Dice expression, e.g. "1d4", "2d6"
 * @property damageType BbF damage type identifier
 * @property conditionId Optional condition identifier. Null = always applies.
 * @property conditionParams Optional parameters for the condition factory.
 */
data class BonusDamageEntry(
    val dice: String,
    val damageType: Identifier,
    val conditionId: Identifier? = null,
    val conditionParams: JsonObject = JsonObject()
) {
    /**
     * Human-readable condition label for tooltips.
     * Resolved via DamageConditionRegistry.getLabel().
     */
    val conditionLabel: String? get() = conditionId?.let {
        DamageConditionRegistry.getLabel(it)
    }
}
