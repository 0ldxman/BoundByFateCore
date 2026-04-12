package omc.boundbyfate.system.combat

import net.minecraft.entity.LivingEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.api.combat.WeaponProperty
import omc.boundbyfate.api.dice.DiceRoller
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfStats
import omc.boundbyfate.registry.WeaponRegistry
import org.slf4j.LoggerFactory

/**
 * Calculates weapon damage using D&D 5e rules.
 *
 * Formula: roll(damageDice) + abilityModifier
 * Critical hit: roll(damageDice) twice + abilityModifier
 *
 * Ability modifier:
 * - FINESSE → max(STR, DEX)
 * - Ranged  → DEX
 * - Default → STR
 *
 * VERSATILE: uses versatileDamage dice when offhand is empty.
 * Fallback: if weapon not in registry, uses vanilla ATTACK_DAMAGE as flat damage.
 */
object WeaponDamageSystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    /**
     * Calculates damage for an attack.
     *
     * @param attacker The attacking entity
     * @param weapon The weapon used
     * @param isCritical Whether this is a critical hit
     * @return Calculated damage value (float for Minecraft compatibility)
     */
    fun calculate(attacker: LivingEntity, weapon: ItemStack, isCritical: Boolean): Float {
        val def = WeaponRegistry.findForItem(weapon)

        if (def == null) {
            // No definition — use vanilla attack damage as flat value
            val vanillaDamage = attacker.getAttributeValue(
                net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE
            ).toFloat()
            return if (isCritical) vanillaDamage * 2f else vanillaDamage
        }

        // Choose dice: versatile two-handed or normal
        val diceExpr = if (
            def.has(WeaponProperty.VERSATILE) &&
            attacker is ServerPlayerEntity &&
            WeaponPropertySystem.isWieldingTwoHanded(attacker) &&
            def.versatileDamage != null
        ) {
            def.versatileDamage
        } else {
            def.damage
        }

        val abilityMod = getAbilityModifier(attacker, weapon, def.properties)

        val roll = if (isCritical) {
            // Crit: roll damage dice twice
            val r1 = DiceRoller.parse(diceExpr)?.total ?: 1
            val r2 = DiceRoller.parse(diceExpr)?.total ?: 1
            r1 + r2
        } else {
            DiceRoller.parse(diceExpr)?.total ?: 1
        }

        val total = (roll + abilityMod).coerceAtLeast(1).toFloat()

        logger.debug(
            "Damage: $diceExpr roll=$roll abilityMod=$abilityMod crit=$isCritical → $total"
        )

        return total
    }

    private fun getAbilityModifier(
        attacker: LivingEntity,
        weapon: ItemStack,
        properties: Set<WeaponProperty>
    ): Int {
        if (attacker !is ServerPlayerEntity) return 0
        val statsData = attacker.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null) ?: return 0

        return when {
            WeaponProperty.FINESSE in properties -> {
                val str = statsData.getStatValue(BbfStats.STRENGTH.id).dndModifier
                val dex = statsData.getStatValue(BbfStats.DEXTERITY.id).dndModifier
                maxOf(str, dex)
            }
            WeaponProperty.THROWN in properties || WeaponProperty.LOADING in properties -> {
                statsData.getStatValue(BbfStats.DEXTERITY.id).dndModifier
            }
            else -> {
                statsData.getStatValue(BbfStats.STRENGTH.id).dndModifier
            }
        }
    }
}
