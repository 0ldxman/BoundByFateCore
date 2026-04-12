package omc.boundbyfate.system.combat

import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.api.combat.WeaponProperty
import omc.boundbyfate.api.dice.DiceRoller
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfStats
import omc.boundbyfate.registry.WeaponRegistry
import org.slf4j.LoggerFactory

object WeaponDamageSystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    fun calculate(attacker: LivingEntity, weapon: ItemStack, isCritical: Boolean): Float {
        val def = WeaponRegistry.findForItem(weapon)

        if (def == null) {
            val vanillaDamage = attacker.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE).toFloat()
            return if (isCritical) vanillaDamage * 2f else vanillaDamage
        }

        val diceExpr = if (
            def.has(WeaponProperty.VERSATILE) &&
            attacker is ServerPlayerEntity &&
            WeaponRegistry.isWieldingTwoHanded(attacker) &&
            def.versatileDamage != null
        ) {
            def.versatileDamage
        } else {
            def.damage
        }

        val abilityMod = getAbilityModifier(attacker, def.properties)

        val roll = if (isCritical) {
            val r1 = DiceRoller.parse(diceExpr)?.total ?: 1
            val r2 = DiceRoller.parse(diceExpr)?.total ?: 1
            r1 + r2
        } else {
            DiceRoller.parse(diceExpr)?.total ?: 1
        }

        val total = (roll + abilityMod).let { if (it < 1) 1 else it }.toFloat()

        logger.debug("Damage: $diceExpr roll=$roll abilityMod=$abilityMod crit=$isCritical -> $total")

        return total
    }

    private fun getAbilityModifier(attacker: LivingEntity, properties: Set<WeaponProperty>): Int {
        if (attacker !is ServerPlayerEntity) return 0
        val statsData = attacker.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null) ?: return 0

        return when {
            WeaponProperty.FINESSE in properties -> {
                val str = statsData.getStatValue(BbfStats.STRENGTH.id).dndModifier
                val dex = statsData.getStatValue(BbfStats.DEXTERITY.id).dndModifier
                if (str > dex) str else dex
            }
            WeaponProperty.THROWN in properties || WeaponProperty.LOADING in properties ->
                statsData.getStatValue(BbfStats.DEXTERITY.id).dndModifier
            else ->
                statsData.getStatValue(BbfStats.STRENGTH.id).dndModifier
        }
    }
}
