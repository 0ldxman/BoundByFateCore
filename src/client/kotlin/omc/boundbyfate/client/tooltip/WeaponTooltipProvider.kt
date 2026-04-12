package omc.boundbyfate.client.tooltip

import net.minecraft.item.ItemStack
import omc.boundbyfate.api.combat.WeaponProperty
import omc.boundbyfate.client.state.ClientWeaponRegistry

/**
 * Tooltip provider that shows weapon combat stats.
 *
 * Shows: damage dice, damage type, and weapon properties.
 * Only shown when SHIFT is held (handled by ItemTooltipManager).
 */
object WeaponTooltipProvider : BbfTooltipProvider {

    override fun getSections(stack: ItemStack, shiftHeld: Boolean): List<BbfTooltipSection> {
        val def = ClientWeaponRegistry.findForItem(stack) ?: return emptyList()

        val lines = mutableListOf<String>()

        // Damage
        val dmgLine = if (def.versatileDamage != null) {
            "${def.damage} (${def.versatileDamage} двуручное)"
        } else {
            def.damage
        }
        lines.add("Урон: $dmgLine")

        // Damage type — strip namespace for display
        val dmgTypeName = def.damageType.path
            .replace("_", " ")
            .replaceFirstChar { it.uppercase() }
        lines.add("Тип урона: $dmgTypeName")

        // Properties
        if (def.properties.isNotEmpty()) {
            val propNames = def.properties
                .sortedBy { it.displayName }
                .joinToString(", ") { it.displayName }
            lines.add("Свойства: $propNames")
        }

        return listOf(BbfTooltipSection(header = def.displayName, lines = lines))
    }
}
