package omc.boundbyfate.system.combat

import net.minecraft.entity.LivingEntity
import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.api.combat.WeaponProperty
import omc.boundbyfate.api.dice.AdvantageType
import omc.boundbyfate.api.dice.DiceRoller
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfStats
import omc.boundbyfate.system.proficiency.ProficiencySystem
import org.slf4j.LoggerFactory

/**
 * Resolves D&D 5e attack rolls.
 *
 * Attack roll: d20 + attack_bonus vs target AC
 *
 * Attack bonus:
 *   = ability_modifier + proficiency_bonus (if proficient)
 *
 * Ability modifier selection:
 *   - FINESSE weapon → max(STR_mod, DEX_mod)
 *   - Ranged weapon  → DEX_mod
 *   - Default melee  → STR_mod
 *
 * Advantage/Disadvantage:
 *   - HEAVY weapon + Small/Tiny attacker → DISADVANTAGE
 *   (advantage/disadvantage cancel each other out if both apply)
 *
 * Critical hit: natural 20 → always hits
 * Critical miss: natural 1 → always misses
 */
object AttackRollSystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    fun resolve(attacker: LivingEntity, target: LivingEntity): AttackResult {
        val targetAc = ArmorClassSystem.getAc(target)
        val attackBonus = getAttackBonus(attacker)
        val advantage = getAdvantageType(attacker)

        val rollResult = DiceRoller.rollD20(advantage = advantage, modifier = attackBonus)
        val rawRoll = rollResult.rolls.let {
            when (advantage) {
                AdvantageType.ADVANTAGE -> it.max()
                AdvantageType.DISADVANTAGE -> it.min()
                AdvantageType.NONE -> it.first()
            }
        }

        val hit = when {
            rawRoll == 20 -> true
            rawRoll == 1  -> false
            else          -> rollResult.total >= targetAc
        }

        val isCrit = rawRoll == 20

        logger.debug(
            "Attack: ${rollResult.expression} total=${rollResult.total} vs AC=$targetAc " +
            "advantage=$advantage → ${if (hit) "HIT${if (isCrit) " (CRIT)" else ""}" else "MISS"}"
        )

        return AttackResult(
            hit = hit,
            isCritical = isCrit,
            roll = rawRoll,
            bonus = attackBonus,
            targetAc = targetAc,
            advantage = advantage
        )
    }

    // ── Attack bonus ──────────────────────────────────────────────────────────

    private fun getAttackBonus(attacker: LivingEntity): Int {
        if (attacker !is ServerPlayerEntity) return 0

        val statsData = attacker.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
            ?: return 0

        val weapon = attacker.mainHandStack
        val properties = WeaponPropertySystem.getProperties(weapon)

        val abilityMod = when {
            WeaponProperty.FINESSE in properties -> {
                // Use whichever is higher
                val strMod = statsData.getStatValue(BbfStats.STRENGTH.id).dndModifier
                val dexMod = statsData.getStatValue(BbfStats.DEXTERITY.id).dndModifier
                maxOf(strMod, dexMod)
            }
            isRangedWeapon(attacker) -> {
                statsData.getStatValue(BbfStats.DEXTERITY.id).dndModifier
            }
            else -> {
                statsData.getStatValue(BbfStats.STRENGTH.id).dndModifier
            }
        }

        val profBonus = if (isProficientWithHeld(attacker)) {
            attacker.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, null)
                ?.getProficiencyBonus() ?: 2
        } else 0

        return abilityMod + profBonus
    }

    // ── Advantage/Disadvantage ────────────────────────────────────────────────

    private fun getAdvantageType(attacker: LivingEntity): AdvantageType {
        if (attacker !is ServerPlayerEntity) return AdvantageType.NONE

        val weapon = attacker.mainHandStack
        var advantageCount = 0
        var disadvantageCount = 0

        // HEAVY weapon + Small/Tiny attacker → disadvantage
        if (WeaponPropertySystem.hasHeavyDisadvantage(attacker, weapon)) {
            disadvantageCount++
        }

        // Advantage and disadvantage cancel each other out
        return when {
            advantageCount > 0 && disadvantageCount > 0 -> AdvantageType.NONE
            advantageCount > 0 -> AdvantageType.ADVANTAGE
            disadvantageCount > 0 -> AdvantageType.DISADVANTAGE
            else -> AdvantageType.NONE
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isRangedWeapon(player: ServerPlayerEntity): Boolean {
        val weapon = player.mainHandStack
        return WeaponPropertySystem.has(weapon, WeaponProperty.THROWN) ||
               WeaponPropertySystem.has(weapon, WeaponProperty.LOADING)
    }

/**
 * Result of an attack roll.
 */
data class AttackResult(
    val hit: Boolean,
    val isCritical: Boolean,
    val roll: Int,
    val bonus: Int,
    val targetAc: Int,
    val advantage: AdvantageType = AdvantageType.NONE
)
