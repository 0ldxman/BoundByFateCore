package omc.boundbyfate.system.combat

import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.api.combat.ArmorType
import omc.boundbyfate.component.EntityArmorClassData
import omc.boundbyfate.registry.BbfArmorTags
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfStats
import org.slf4j.LoggerFactory

/**
 * Computes and applies Armor Class for entities.
 *
 * Formula:
 *   AC = 10 + armorValue + dexBonus(capped by armor type) + shieldBonus
 *
 * armorValue = sum of ARMOR attribute from all equipped armor pieces
 * dexBonus   = DEX modifier, capped by armor type (light=∞, medium=+2, heavy=0)
 * shieldBonus = +2 if shield is in off-hand
 *
 * STR requirement: heavy armor with NBT tag "StrRequirement" checks player STR.
 * If not met → speed penalty applied via attribute modifier.
 */
object ArmorClassSystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    const val SHIELD_BONUS = 2
    private val SPEED_PENALTY_UUID = java.util.UUID.fromString("bbf00002-0000-0000-0000-000000000002")
    private const val SPEED_PENALTY_NAME = "BbF Heavy Armor STR Penalty"
    private const val SPEED_PENALTY = -0.15 // -15% movement speed

    /**
     * Recalculates and stores AC for the given entity.
     * Call this whenever equipment or DEX stat changes.
     */
    fun recalculate(entity: LivingEntity) {
        val armorValue = getArmorValue(entity)
        val armorType = getArmorType(entity)
        val dexBonus = getDexBonus(entity, armorType)
        val shieldBonus = if (hasShield(entity)) SHIELD_BONUS else 0
        val strMet = checkStrRequirement(entity)

        val ac = 10 + armorValue + dexBonus + shieldBonus

        val data = EntityArmorClassData(baseAc = ac, strRequirementMet = strMet)
        entity.setAttached(BbfAttachments.ENTITY_ARMOR_CLASS, data)

        applyStrPenalty(entity, strMet)

        logger.debug(
            "AC for ${if (entity is ServerPlayerEntity) entity.name.string else entity.type.name.string}: " +
            "armor=$armorValue type=$armorType dex=$dexBonus shield=$shieldBonus → AC=$ac strMet=$strMet"
        )
    }

    /**
     * Returns the current AC for an entity. Defaults to 10 if not calculated yet.
     */
    fun getAc(entity: LivingEntity): Int =
        entity.getAttachedOrElse(BbfAttachments.ENTITY_ARMOR_CLASS, EntityArmorClassData()).baseAc

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Sums the vanilla ARMOR attribute value from all equipped armor pieces. */
    private fun getArmorValue(entity: LivingEntity): Int {
        return entity.getAttributeValue(EntityAttributes.GENERIC_ARMOR).toInt()
    }

    /** Determines the most restrictive armor type worn. */
    private fun getArmorType(entity: LivingEntity): ArmorType {
        val armorSlots = listOf(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
        )

        var heaviest = ArmorType.NONE
        for (slot in armorSlots) {
            val stack = entity.getEquippedStack(slot)
            if (stack.isEmpty) continue
            val type = getItemArmorType(stack)
            if (type.ordinal > heaviest.ordinal) heaviest = type
        }
        return heaviest
    }

    /** Returns the armor type of a single item based on tags. */
    fun getItemArmorType(stack: ItemStack): ArmorType = when {
        stack.isIn(BbfArmorTags.HEAVY) -> ArmorType.HEAVY
        stack.isIn(BbfArmorTags.MEDIUM) -> ArmorType.MEDIUM
        stack.isIn(BbfArmorTags.LIGHT) -> ArmorType.LIGHT
        else -> ArmorType.NONE
    }

    /** Returns DEX modifier capped by armor type. */
    private fun getDexBonus(entity: LivingEntity, armorType: ArmorType): Int {
        val dexMod = if (entity is ServerPlayerEntity) {
            entity.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
                ?.getStatValue(BbfStats.DEXTERITY.id)?.dndModifier ?: 0
        } else {
            // For mobs: derive from vanilla speed attribute as rough approximation
            0
        }
        return dexMod.coerceAtMost(armorType.dexCap)
    }

    /** Returns true if entity has a shield in off-hand. */
    private fun hasShield(entity: LivingEntity): Boolean {
        val offhand = entity.getEquippedStack(EquipmentSlot.OFFHAND)
        return !offhand.isEmpty && offhand.isIn(BbfArmorTags.SHIELD)
    }

    /**
     * Checks if the entity meets the STR requirement of worn heavy armor.
     * STR requirement is stored as NBT int "BbfStrReq" on the item.
     */
    private fun checkStrRequirement(entity: LivingEntity): Boolean {
        if (entity !is ServerPlayerEntity) return true

        val strValue = entity.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
            ?.getStatValue(BbfStats.STRENGTH.id)?.total ?: 10

        val armorSlots = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
        for (slot in armorSlots) {
            val stack = entity.getEquippedStack(slot)
            if (stack.isEmpty || !stack.isIn(BbfArmorTags.HEAVY)) continue
            val req = getStrRequirement(stack)
            if (req > 0 && strValue < req) return false
        }
        return true
    }

    /**
     * Reads STR requirement from item NBT.
     * Set via: /item modify ... with nbt {BbfStrReq: 15}
     */
    fun getStrRequirement(stack: ItemStack): Int {
        val nbt: NbtCompound? = stack.nbt
        return nbt?.getInt("BbfStrReq") ?: 0
    }

    /** Applies or removes speed penalty based on STR requirement. */
    private fun applyStrPenalty(entity: LivingEntity, strMet: Boolean) {
        val speedAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED) ?: return

        // Remove existing penalty
        speedAttr.getModifier(SPEED_PENALTY_UUID)?.let { speedAttr.removeModifier(it) }

        if (!strMet) {
            speedAttr.addPersistentModifier(
                net.minecraft.entity.attribute.EntityAttributeModifier(
                    SPEED_PENALTY_UUID,
                    SPEED_PENALTY_NAME,
                    SPEED_PENALTY,
                    net.minecraft.entity.attribute.EntityAttributeModifier.Operation.MULTIPLY_TOTAL
                )
            )
        }
    }
}
