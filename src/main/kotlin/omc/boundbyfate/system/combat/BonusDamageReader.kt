package omc.boundbyfate.system.combat

import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.ZombieEntity
import net.minecraft.entity.mob.SkeletonEntity
import net.minecraft.entity.mob.PhantomEntity
import net.minecraft.entity.mob.DrownedEntity
import net.minecraft.entity.mob.ZombieVillagerEntity
import net.minecraft.entity.mob.WitherSkeletonEntity
import net.minecraft.entity.mob.StrayEntity
import net.minecraft.entity.mob.HuskEntity
import net.minecraft.entity.mob.ZombifiedPiglinEntity
import net.minecraft.entity.mob.WardenEntity
import net.minecraft.entity.boss.WitherEntity
import net.minecraft.entity.passive.IronGolemEntity
import net.minecraft.entity.passive.SnowGolemEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.util.Identifier
import omc.boundbyfate.api.combat.BonusDamageEntry

/**
 * Reads bonus damage entries from ItemStack NBT.
 *
 * NBT format:
 * ```
 * BbfBonusDamage: [
 *   {dice: "1d4", type: "boundbyfate-core:fire"},
 *   {dice: "1d6", type: "boundbyfate-core:radiant", condition: "undead"}
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
            val condition = compound.getString("condition").takeIf { it.isNotBlank() }
            try {
                BonusDamageEntry(dice, Identifier(typeStr), condition)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Filters bonus damage entries that apply to the given target.
     */
    fun getApplicableEntries(stack: ItemStack, target: LivingEntity): List<BonusDamageEntry> {
        return readEntries(stack).filter { entry ->
            when (entry.condition) {
                null -> true
                "undead" -> isUndead(target)
                "construct" -> isConstruct(target)
                else -> false
            }
        }
    }

    private fun isUndead(entity: LivingEntity): Boolean = entity is ZombieEntity ||
        entity is SkeletonEntity || entity is PhantomEntity ||
        entity is DrownedEntity || entity is ZombieVillagerEntity ||
        entity is WitherSkeletonEntity || entity is StrayEntity ||
        entity is HuskEntity || entity is ZombifiedPiglinEntity ||
        entity is WitherEntity || entity is WardenEntity

    private fun isConstruct(entity: LivingEntity): Boolean =
        entity is IronGolemEntity || entity is SnowGolemEntity
}
