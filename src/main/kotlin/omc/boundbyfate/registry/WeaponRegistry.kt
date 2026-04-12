package omc.boundbyfate.registry

import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.api.combat.WeaponDefinition
import omc.boundbyfate.api.combat.WeaponProperty
import omc.boundbyfate.api.race.RaceSize
import omc.boundbyfate.registry.BbfAttachments
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry for all weapon definitions.
 * Populated by [omc.boundbyfate.config.WeaponDatapackLoader] on server start.
 */
object WeaponRegistry {
    private val byId = ConcurrentHashMap<Identifier, WeaponDefinition>()
    // item ID → weapon definition (fast lookup during combat)
    private val byItem = ConcurrentHashMap<Identifier, WeaponDefinition>()

    fun register(definition: WeaponDefinition) {
        byId[definition.id] = definition
        definition.items.forEach { itemId -> byItem[itemId] = definition }
    }

    fun get(id: Identifier): WeaponDefinition? = byId[id]

    /** Finds the weapon definition for the given item stack. Returns null if not registered. */
    fun findForItem(stack: ItemStack): WeaponDefinition? {
        if (stack.isEmpty) return null
        val itemId = Registries.ITEM.getId(stack.item)
        return byItem[itemId]
    }

    fun getAll(): Collection<WeaponDefinition> = byId.values.toList()

    fun clearAll() {
        byId.clear()
        byItem.clear()
    }

    val size: Int get() = byId.size

    // ── Property helpers ──────────────────────────────────────────────────────

    fun getProperties(stack: ItemStack): Set<WeaponProperty> =
        findForItem(stack)?.properties ?: emptySet()

    fun has(stack: ItemStack, property: WeaponProperty): Boolean =
        findForItem(stack)?.has(property) == true

    fun applyPassiveEffects(player: ServerPlayerEntity, heldItem: ItemStack) {
        // REACH not available in 1.20.1
    }

    fun isWieldingTwoHanded(player: ServerPlayerEntity): Boolean =
        player.getEquippedStack(EquipmentSlot.OFFHAND).isEmpty

    fun isTwoHandedViolation(player: ServerPlayerEntity): Boolean {
        val mainHand = player.mainHandStack
        if (!has(mainHand, WeaponProperty.TWO_HANDED)) return false
        return !player.getEquippedStack(EquipmentSlot.OFFHAND).isEmpty
    }

    fun hasHeavyDisadvantage(player: ServerPlayerEntity, weapon: ItemStack): Boolean {
        if (!has(weapon, WeaponProperty.HEAVY)) return false
        val raceData = player.getAttachedOrElse(BbfAttachments.PLAYER_RACE, null) ?: return false
        val race = omc.boundbyfate.registry.RaceRegistry.getRace(raceData.raceId) ?: return false
        return race.size == RaceSize.SMALL || race.size == RaceSize.TINY
    }
}
