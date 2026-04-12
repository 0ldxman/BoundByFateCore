package omc.boundbyfate.system.combat

import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.api.combat.WeaponProperty
import omc.boundbyfate.api.race.RaceSize
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfWeaponTags
import omc.boundbyfate.registry.RaceRegistry
import java.util.UUID

/**
 * Reads weapon properties from item tags and applies passive effects.
 *
 * Passive effects (applied when item is held):
 * - REACH: +1 to ATTACK_RANGE attribute
 * - TWO_HANDED: blocks offhand slot (enforced in mixin)
 *
 * Active effects (used during attack roll resolution in AttackRollSystem):
 * - FINESSE: use max(STR, DEX) modifier
 * - HEAVY: disadvantage for Small/Tiny creatures
 * - VERSATILE: bonus damage die when offhand is empty
 */
object WeaponPropertySystem {
    private val REACH_MODIFIER_UUID = UUID.fromString("bbf00003-0000-0000-0000-000000000003")
    private const val REACH_MODIFIER_NAME = "BbF Reach Weapon"
    private const val REACH_BONUS = 1.0

    /**
     * Returns all weapon properties for the given item stack.
     */
    fun getProperties(stack: ItemStack): Set<WeaponProperty> {
        if (stack.isEmpty) return emptySet()
        return BbfWeaponTags.ALL
            .filter { (_, tag) -> stack.isIn(tag) }
            .map { (prop, _) -> prop }
            .toSet()
    }

    fun has(stack: ItemStack, property: WeaponProperty): Boolean =
        stack.isIn(BbfWeaponTags.ALL.first { it.first == property }.second)

    /**
     * Applies/removes passive attribute effects based on held weapon.
     * Call when player changes main hand item.
     */
    fun applyPassiveEffects(player: ServerPlayerEntity, heldItem: ItemStack) {
        applyReach(player, heldItem)
    }

    /**
     * Returns true if the attacker should roll with disadvantage due to HEAVY property.
     * Applies to Small and Tiny creatures.
     */
    fun hasHeavyDisadvantage(player: ServerPlayerEntity, weapon: ItemStack): Boolean {
        if (!has(weapon, WeaponProperty.HEAVY)) return false
        val raceData = player.getAttachedOrElse(BbfAttachments.PLAYER_RACE, null) ?: return false
        val race = RaceRegistry.getRace(raceData.raceId) ?: return false
        return race.size == RaceSize.SMALL || race.size == RaceSize.TINY
    }

    /**
     * Returns true if offhand is empty (relevant for VERSATILE two-handed bonus damage).
     */
    fun isWieldingTwoHanded(player: ServerPlayerEntity): Boolean {
        val offhand = player.getEquippedStack(EquipmentSlot.OFFHAND)
        return offhand.isEmpty
    }

    /**
     * Returns true if TWO_HANDED weapon is held and offhand has an item (invalid state).
     */
    fun isTwoHandedViolation(player: ServerPlayerEntity): Boolean {
        val mainHand = player.mainHandStack
        if (!has(mainHand, WeaponProperty.TWO_HANDED)) return false
        val offhand = player.getEquippedStack(EquipmentSlot.OFFHAND)
        return !offhand.isEmpty
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun applyReach(player: ServerPlayerEntity, heldItem: ItemStack) {
        val attr = player.getAttributeInstance(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE) ?: return

        // Remove existing reach modifier
        attr.getModifier(REACH_MODIFIER_UUID)?.let { attr.removeModifier(it) }

        if (has(heldItem, WeaponProperty.REACH)) {
            attr.addTemporaryModifier(
                EntityAttributeModifier(
                    REACH_MODIFIER_UUID,
                    REACH_MODIFIER_NAME,
                    REACH_BONUS,
                    EntityAttributeModifier.Operation.ADDITION
                )
            )
        }
    }
}
