package omc.boundbyfate.registry

import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import omc.boundbyfate.api.combat.WeaponDefinition
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
}
