package omc.boundbyfate.system.combat

import net.minecraft.entity.LivingEntity
import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.api.dice.DiceRoller
import omc.boundbyfate.api.dice.DiceType
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfStats
import omc.boundbyfate.system.proficiency.ProficiencySystem
import org.slf4j.LoggerFactory

/**
 * Resolves attack rolls using D&D 5e rules.
 *
 * Attack roll: d20 + attack bonus vs target AC
 * Attack bonus = STR modifier (melee) or DEX modifier (ranged/finesse) + proficiency bonus (if proficient)
 *
 * Critical hit: natural 20 → always hits
 * Critical miss: natural 1 → always misses
 */
object AttackRollSystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    /**
     * Resolves whether an attack hits.
     *
     * @param attacker The attacking entity
     * @param target The defending entity
     * @return AttackResult with hit/miss and roll details
     */
    fun resolve(attacker: LivingEntity, target: LivingEntity): AttackResult {
        val targetAc = ArmorClassSystem.getAc(target)
        val roll = DiceRoller.roll(DiceType.D20)
        val attackBonus = getAttackBonus(attacker)
        val total = roll + attackBonus

        val hit = when {
            roll == 20 -> true   // natural 20 — critical hit
            roll == 1 -> false   // natural 1 — critical miss
            else -> total >= targetAc
        }

        val isCrit = roll == 20

        logger.debug(
            "Attack: roll=$roll bonus=$attackBonus total=$total vs AC=$targetAc → ${if (hit) "HIT${if (isCrit) " (CRIT)" else ""}" else "MISS"}"
        )

        return AttackResult(hit = hit, isCritical = isCrit, roll = roll, bonus = attackBonus, targetAc = targetAc)
    }

    /**
     * Calculates attack bonus for an entity.
     * Players: STR mod + proficiency if proficient with held weapon.
     * Mobs: 0 (can be extended later via mob stats).
     */
    private fun getAttackBonus(attacker: LivingEntity): Int {
        if (attacker !is ServerPlayerEntity) return 0

        val statsData = attacker.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
            ?: return 0

        val heldItem = attacker.mainHandStack
        val isRanged = heldItem.isIn(net.minecraft.item.Items.BOW.defaultStack.let {
            net.minecraft.registry.tag.TagKey.of(
                net.minecraft.registry.RegistryKeys.ITEM,
                net.minecraft.util.Identifier("boundbyfate-core", "proficiency/bows")
            )
        })

        // Use DEX for ranged, STR for melee
        val statId = if (isRanged) BbfStats.DEXTERITY.id else BbfStats.STRENGTH.id
        val statMod = statsData.getStatValue(statId).dndModifier

        // Add proficiency bonus if proficient with held weapon
        val profBonus = if (isProficientWithHeld(attacker)) {
            attacker.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, null)
                ?.getProficiencyBonus() ?: 2
        } else 0

        return statMod + profBonus
    }

    private fun isProficientWithHeld(player: ServerPlayerEntity): Boolean {
        val item = player.mainHandStack
        if (item.isEmpty) return true // unarmed
        val prof = ProficiencySystem.findItemProficiency(item) ?: return true // no proficiency required
        return ProficiencySystem.hasProficiency(player, prof.id)
    }
}

/**
 * Result of an attack roll resolution.
 */
data class AttackResult(
    val hit: Boolean,
    val isCritical: Boolean,
    val roll: Int,
    val bonus: Int,
    val targetAc: Int
)
