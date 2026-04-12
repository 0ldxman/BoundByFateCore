package omc.boundbyfate.system.combat

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.entity.LivingEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.util.Identifier
import omc.boundbyfate.api.combat.BonusDamageEntry
import omc.boundbyfate.registry.DamageConditionRegistry

/**
 * Reads bonus damage entries from ItemStack NBT and evaluates conditions.
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
 */
object BonusDamageReader {

    private const val NBT_KEY = "BbfBonusDamage"

    /**
     * Reads all bonus damage entries from the item's NBT.
     */
    fun readEntries(stack: ItemStack): List<BonusDamageEntry> {
        val nbt: NbtCompound = stack.nbt ?: return emptyList()
        val list: NbtList = nbt.getList(NBT_KEY, 10) // 10 = NbtCompound type
        if (list.isEmpty()) return emptyList()

        return list.mapNotNull { element ->
            val compound = element as? NbtCompound ?: return@mapNotNull null
            val dice = compound.getString("dice").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val typeStr = compound.getString("type").takeIf { it.isNotBlank() } ?: return@mapNotNull null

            val conditionId = compound.getString("condition").takeIf { it.isNotBlank() }
                ?.let { try { Identifier(it) } catch (e: Exception) { null } }

            // Parse conditionParams from NBT string (stored as JSON string in NBT)
            val conditionParams = if (compound.contains("conditionParams")) {
                try {
                    JsonParser.parseString(compound.getString("conditionParams")) as? JsonObject
                        ?: JsonObject()
                } catch (e: Exception) { JsonObject() }
            } else JsonObject()

            try {
                BonusDamageEntry(dice, Identifier(typeStr), conditionId, conditionParams)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Returns only entries whose condition passes for the given attacker/target pair.
     */
    fun getApplicableEntries(
        stack: ItemStack,
        attacker: LivingEntity,
        target: LivingEntity
    ): List<BonusDamageEntry> {
        return readEntries(stack).filter { entry ->
            val condId = entry.conditionId
                ?: return@filter true // no condition = always applies

            val condition = DamageConditionRegistry.create(condId, entry.conditionParams)
            if (condition == null) {
                // Unknown condition — skip this entry
                return@filter false
            }
            condition.test(attacker, target)
        }
    }
}
