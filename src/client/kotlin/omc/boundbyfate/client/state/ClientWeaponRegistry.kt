package omc.boundbyfate.client.state

import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import omc.boundbyfate.api.combat.WeaponDefinition
import omc.boundbyfate.api.combat.WeaponProperty

/**
 * Client-side mirror of WeaponRegistry.
 * Populated via network packet when player joins the server.
 * Used by tooltip providers to show weapon info without server access.
 */
object ClientWeaponRegistry {
    private val byItem = mutableMapOf<Identifier, WeaponDefinition>()

    fun update(definitions: List<WeaponDefinition>) {
        byItem.clear()
        definitions.forEach { def ->
            def.items.forEach { itemId -> byItem[itemId] = def }
        }
    }

    fun findForItem(stack: ItemStack): WeaponDefinition? {
        if (stack.isEmpty) return null
        val itemId = Registries.ITEM.getId(stack.item)
        return byItem[itemId]
    }

    fun clear() = byItem.clear()
}
