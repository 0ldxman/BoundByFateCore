package omc.boundbyfate.system.combat

import net.minecraft.entity.LivingEntity
import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.api.combat.WeaponProperty
import omc.boundbyfate.api.dice.AdvantageType
import omc.boundbyfate.api.dice.DiceRoller
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfStats
import omc.boundbyfate.registry.WeaponRegistry
import omc.boundbyfate.system.proficiency.ProficiencySystem
import org.slf4j.LoggerFactory

object AttackRollSystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    fun resolve(attacker: LivingEntity, target: LivingEntity): AttackResult {
        val targetAc = ArmorClassSystem.getAc(target)
        val attackBonus = getAttackBonus(attacker)
        val advantage = getAdvantageType(attacker)

        val rollResult = DiceRoller.rollD20(advantage = advantage, modifier = attackBonus)
        val rawRoll = when (advantage) {
            AdvantageType.ADVANTAGE -> rollResult.rolls.max()
            AdvantageType.DISADVANTAGE -> rollResult.rolls.min()
            AdvantageType.NONE -> rollResult.rolls.first()
        }

        val hit = when {
            rawRoll == 20 -> true
            rawRoll == 1  -> false
            else          -> rollResult.total >= targetAc
        }

        val isCrit = rawRoll == 20

        logger.debug(
            "Attack: ${rollResult.expression} total=${rollResult.total} vs AC=$targetAc " +
            "advantage=$advantage -> ${if (hit) "HIT${if (isCrit) " (CRIT)" else ""}" else "MISS"}"
        )

        return AttackResult(
            hit = hit,
            critical = isCrit,
            roll = rawRoll,
            bonus = attackBonus,
            targetAc = targetAc,
            advantage = advantage
        )
    }

    private fun getAttackBonus(attacker: LivingEntity): Int {
        if (attacker !is ServerPlayerEntity) return 0
        val statsData = attacker.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null) ?: return 0

        val weapon = attacker.mainHandStack
        val properties = WeaponRegistry.getProperties(weapon)

        val abilityMod = when {
            WeaponProperty.FINESSE in properties -> {
                val strMod = statsData.getStatValue(BbfStats.STRENGTH.id).dndModifier
                val dexMod = statsData.getStatValue(BbfStats.DEXTERITY.id).dndModifier
                if (strMod > dexMod) strMod else dexMod
            }
            isRangedWeapon(attacker) -> statsData.getStatValue(BbfStats.DEXTERITY.id).dndModifier
            else -> statsData.getStatValue(BbfStats.STRENGTH.id).dndModifier
        }

        val profBonus = if (isProficientWithHeld(attacker)) {
            attacker.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, null)?.getProficiencyBonus() ?: 2
        } else 0

        return abilityMod + profBonus
    }

    private fun getAdvantageType(attacker: LivingEntity): AdvantageType {
        if (attacker !is ServerPlayerEntity) return AdvantageType.NONE

        val weapon = attacker.mainHandStack
        var advantageCount = 0
        var disadvantageCount = 0

        if (WeaponRegistry.hasHeavyDisadvantage(attacker, weapon)) disadvantageCount++

        return when {
            advantageCount > 0 && disadvantageCount > 0 -> AdvantageType.NONE
            advantageCount > 0 -> AdvantageType.ADVANTAGE
            disadvantageCount > 0 -> AdvantageType.DISADVANTAGE
            else -> AdvantageType.NONE
        }
    }

    private fun isRangedWeapon(player: ServerPlayerEntity): Boolean {
        val weapon = player.mainHandStack
        return WeaponRegistry.has(weapon, WeaponProperty.THROWN) ||
               WeaponRegistry.has(weapon, WeaponProperty.LOADING)
    }

    private fun isProficientWithHeld(player: ServerPlayerEntity): Boolean {
        val item = player.mainHandStack
        // Unarmed — no proficiency bonus by default (requires Tavern Brawler feat or similar)
        if (item.isEmpty) return false

        // If item is not a registered weapon — treat as improvised, no proficiency bonus
        val weaponDef = omc.boundbyfate.registry.WeaponRegistry.findForItem(item)
            ?: return false

        // Item is a weapon — check if player has the required proficiency
        val prof = ProficiencySystem.findItemProficiency(item)
            // Weapon has no proficiency requirement — bonus applies
            ?: return true

        return ProficiencySystem.hasProficiency(player, prof.id)
    }
}

data class AttackResult(
    val hit: Boolean,
    val critical: Boolean,
    val roll: Int,
    val bonus: Int,
    val targetAc: Int,
    val advantage: AdvantageType = AdvantageType.NONE
)