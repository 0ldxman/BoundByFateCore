package omc.boundbyfate.system.combat

import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.api.combat.BonusDamageEntry
import omc.boundbyfate.api.combat.WeaponProperty
import omc.boundbyfate.api.dice.DiceRoller
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfStats
import omc.boundbyfate.registry.WeaponRegistry
import org.slf4j.LoggerFactory

/**
 * Calculates weapon damage including bonus damage entries from NBT.
 *
 * Main damage: roll(damageDice) + abilityModifier
 * Bonus damage: each BonusDamageEntry is applied separately via its own DamageSource
 *               so resistances/immunities are applied per damage type.
 */
object WeaponDamageSystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    /**
     * Calculates main weapon damage (base dice + ability modifier).
     * Bonus damage entries are applied separately via [applyBonusDamage].
     */
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
        ) def.versatileDamage else def.damage

        val abilityMod = getAbilityModifier(attacker, def.properties)

        val roll = if (isCritical) {
            (DiceRoller.parse(diceExpr)?.total ?: 1) + (DiceRoller.parse(diceExpr)?.total ?: 1)
        } else {
            DiceRoller.parse(diceExpr)?.total ?: 1
        }

        val total = (roll + abilityMod).let { if (it < 1) 1 else it }.toFloat()
        logger.debug("Damage: $diceExpr roll=$roll abilityMod=$abilityMod crit=$isCritical -> $total")
        return total
    }

    /**
     * Applies all applicable bonus damage entries from the weapon's NBT to the target.
     * Each entry uses its own damage type so resistances are applied correctly.
     *
     * Call this after the main damage has been applied.
     */
    fun applyBonusDamage(
        attacker: LivingEntity,
        weapon: ItemStack,
        target: LivingEntity,
        isCritical: Boolean
    ) {
        val entries = BonusDamageReader.getApplicableEntries(weapon, target)
        if (entries.isEmpty()) return

        val world = attacker.world
        if (world.isClient) return

        for (entry in entries) {
            val roll = if (isCritical) {
                (DiceRoller.parse(entry.dice)?.total ?: 1) + (DiceRoller.parse(entry.dice)?.total ?: 1)
            } else {
                DiceRoller.parse(entry.dice)?.total ?: 1
            }

            if (roll <= 0) continue

            // Use a generic damage source — resistance system reads the BbF damage type
            // from the target's EntityDamageData which is keyed by our custom type IDs
            val source = world.damageSources.generic()
            target.damage(source, roll.toFloat())

            logger.debug("Bonus damage: ${entry.dice} -> $roll (${entry.damageType}) crit=$isCritical")
        }
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
