package omc.boundbyfate.client.tooltip

import net.minecraft.item.ItemStack
import omc.boundbyfate.api.combat.WeaponProperty
import omc.boundbyfate.client.state.ClientWeaponRegistry
import omc.boundbyfate.system.combat.BonusDamageReader

/**
 * Tooltip provider that shows weapon combat stats.
 *
 * Format:
 *   Длинный меч
 *     2d6 (Рубящий)
 *     + 1d4 (Огонь)
 *     + 1d6 (Сияющий) [vs Нежить]
 *     Свойства: Фехтовальное, Универсальное
 */
object WeaponTooltipProvider : BbfTooltipProvider {

    override fun getSections(stack: ItemStack, shiftHeld: Boolean): List<BbfTooltipSection> {
        val def = ClientWeaponRegistry.findForItem(stack) ?: return emptyList()

        val lines = mutableListOf<String>()

        // Main damage line
        val mainDmgType = def.damageType.path.replace("_", " ").replaceFirstChar { it.uppercase() }
        val mainDmg = if (def.versatileDamage != null) {
            "${def.damage} / ${def.versatileDamage} двуручное ($mainDmgType)"
        } else {
            "${def.damage} ($mainDmgType)"
        }
        lines.add(mainDmg)

        // Bonus damage entries from NBT
        val bonusEntries = BonusDamageReader.readEntries(stack)
        for (entry in bonusEntries) {
            val typeName = entry.damageType.path.replace("_", " ").replaceFirstChar { it.uppercase() }
            val conditionStr = entry.conditionLabel?.let { " [$it]" } ?: ""
            lines.add("+ ${entry.dice} ($typeName)$conditionStr")
        }

        // Properties
        if (def.properties.isNotEmpty()) {
            val propNames = def.properties.sortedBy { it.displayName }.joinToString(", ") { it.displayName }
            lines.add("Свойства: $propNames")
        }

        return listOf(BbfTooltipSection(header = def.displayName, lines = lines))
    }
}
