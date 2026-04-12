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
 * Properties are defined in bbf_weapon/*.json datapacks.
 */
object WeaponPropertySystem {

    fun getProperties(stack: ItemStack): Set<WeaponProperty> =
        WeaponRegistry.findForItem(stack)?.properties ?: emptySet()

    fun has(stack: ItemStack, property: WeaponProperty): Boolean =
        WeaponRegistry.findForItem(stack)?.has(property) == true

    fun applyPassiveEffects(player: ServerPlayerEntity, heldItem: ItemStack) {
        // REACH not available in 1.20.1
    }

    fun hasHeavyDisadvantage(player: ServerPlayerEntity, weapon: ItemStack): Boolean {
        if (!has(weapon, WeaponProperty.HEAVY)) return false
        val raceData = player.getAttachedOrElse(BbfAttachments.PLAYER_RACE, null) ?: return false
        val race = RaceRegistry.getRace(raceData.raceId) ?: return false
        return race.size == RaceSize.SMALL || race.size == RaceSize.TINY
    }

    fun isWieldingTwoHanded(player: ServerPlayerEntity): Boolean =
        player.getEquippedStack(EquipmentSlot.OFFHAND).isEmpty

    fun isTwoHandedViolation(player: ServerPlayerEntity): Boolean {
        val mainHand = player.mainHandStack
        if (!has(mainHand, WeaponProperty.TWO_HANDED)) return false
        return !player.getEquippedStack(EquipmentSlot.OFFHAND).isEmpty
    }
}
