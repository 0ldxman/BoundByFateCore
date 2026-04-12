package omc.boundbyfate.system.combat

import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.api.combat.WeaponProperty
import omc.boundbyfate.api.race.RaceSize
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.RaceRegistry
import omc.boundbyfate.registry.WeaponRegistry

/**
 * Reads weapon properties from WeaponRegistry and applies passive effects.
 *
 * Properties are defined in bbf_weapon/*.json datapacks.
 * Falls back to empty set if weapon is not registered.
 */
object WeaponPropertySystem {

    fun getProperties(stack: ItemStack): Set<WeaponProperty> =
        WeaponRegistry.findForItem(stack)?.properties ?: emptySet()

    fun has(stack: ItemStack, property: WeaponProperty): Boolean =
        WeaponRegistry.findForItem(stack)?.has(property) == true

    /**
     * Applies passive effects when main hand changes.
     * Currently a no-op — REACH attribute not available in 1.20.1.
     */
    fun applyPassiveEffects(player: ServerPlayerEntity, heldItem: ItemStack) {
        // REACH: PLAYER_ENTITY_INTERACTION_RANGE not available in 1.20.1
        // Will be implemented on version upgrade
    }

    /**
     * Returns true if attacker should roll with disadvantage due to HEAVY weapon.
     * Applies to Small and Tiny creatures.
     */
    fun hasHeavyDisadvantage(player: ServerPlayerEntity, weapon: ItemStack): Boolean {
        if (!has(weapon, WeaponProperty.HEAVY)) return false
        val raceData = player.getAttachedOrElse(BbfAttachments.PLAYER_RACE, null) ?: return false
        val race = RaceRegistry.getRace(raceData.raceId) ?: return false
        return race.size == RaceSize.SMALL || race.size == RaceSize.TINY
    }

    /** Returns true if offhand is empty (relevant for VERSATILE two-handed bonus damage). */
    fun isWieldingTwoHanded(player: ServerPlayerEntity): Boolean =
        player.getEquippedStack(EquipmentSlot.OFFHAND).isEmpty

    /** Returns true if TWO_HANDED weapon is held and offhand has an item. */
    fun isTwoHandedViolation(player: ServerPlayerEntity): Boolean {
        val mainHand = player.mainHandStack
        if (!has(mainHand, WeaponProperty.TWO_HANDED)) return false
        return !player.getEquippedStack(EquipmentSlot.OFFHAND).isEmpty
    }
}
