package omc.boundbyfate.system.check

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import omc.boundbyfate.api.dice.DiceRoller
import omc.boundbyfate.api.dice.DiceType
import omc.boundbyfate.component.PlayerLevelData
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.SkillRegistry
import org.slf4j.LoggerFactory

/**
 * Handles skill checks and saving throws for players.
 *
 * Usage:
 * ```kotlin
 * // Visible check with DC
 * val result = CheckSystem.check(player, CheckRequest(
 *     skillId = BbfSkills.ATHLETICS.id,
 *     dc = 15,
 *     visible = true
 * ))
 *
 * // Hidden check (player doesn't see the roll)
 * val result = CheckSystem.check(player, CheckRequest(
 *     skillId = BbfSkills.PERCEPTION.id,
 *     dc = 12,
 *     visible = false,
 *     reason = "Заметить засаду"
 * ))
 * ```
 */
object CheckSystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    /**
     * Performs a skill check or saving throw for a player.
     *
     * @param player The player making the check
     * @param request The check parameters
     * @return CheckResult with full details, or null if skill not found
     */
    fun check(player: ServerPlayerEntity, request: CheckRequest): CheckResult? {
        val skillDef = SkillRegistry.get(request.skillId) ?: run {
            logger.warn("Unknown skill ID for check: ${request.skillId}")
            return null
        }

        // Get stat modifier
        val statsData = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
        val statValue = statsData?.getStatValue(skillDef.linkedStat)
        val statModifier = statValue?.dndModifier ?: 0

        // Get proficiency bonus
        val levelData = player.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, PlayerLevelData())
        val baseProficiencyBonus = levelData.getProficiencyBonus()

        val skillData = player.getAttachedOrElse(BbfAttachments.ENTITY_SKILLS, null)
        val proficiencyLevel = skillData?.getProficiency(request.skillId)
        val proficiencyBonus = baseProficiencyBonus * (proficiencyLevel?.multiplier ?: 0)

        // Roll d20
        val diceResult = DiceRoller.rollD20(request.advantage, modifier = statModifier + proficiencyBonus)

        // Calculate success
        val success = request.dc?.let { diceResult.total >= it }

        val result = CheckResult(
            request = request,
            diceResult = diceResult,
            statModifier = statModifier,
            proficiencyBonus = proficiencyBonus,
            total = diceResult.total,
            success = success
        )

        // Log for DM
        val logMsg = buildString {
            append("[Check] ${player.name.string} | ${skillDef.displayName}")
            if (request.reason != null) append(" (${request.reason})")
            append(" | Roll: ${diceResult.rolls} stat:$statModifier prof:$proficiencyBonus = ${result.total}")
            if (request.dc != null) append(" vs DC${request.dc} → ${if (success == true) "SUCCESS" else "FAIL"}")
        }
        logger.info(logMsg)

        // Send feedback to player if visible
        if (request.visible) {
            sendRollMessage(player, skillDef.displayName, result)
        } else if (result.isCriticalSuccess || result.isCriticalFailure) {
            // Always show crits even on hidden rolls
            sendCritMessage(player, result)
        }

        return result
    }

    private fun sendRollMessage(player: ServerPlayerEntity, skillName: String, result: CheckResult) {
        when {
            result.isCriticalSuccess -> {
                player.sendMessage(Text.literal("§6[Бросок] §a✦ КРИТИЧЕСКИЙ УСПЕХ! §7$skillName [20]"), false)
            }
            result.isCriticalFailure -> {
                player.sendMessage(Text.literal("§6[Бросок] §c✦ КРИТИЧЕСКИЙ ПРОВАЛ! §7$skillName [1]"), false)
            }
            else -> {
                val rollStr = buildRollString(result)
                val dcStr = result.request.dc?.let { dc ->
                    val successStr = if (result.success == true) "§aУспех" else "§cПровал"
                    " §7vs DC$dc → $successStr"
                } ?: ""
                player.sendMessage(Text.literal("§6[Бросок] §e$skillName: §f$rollStr$dcStr"), false)
            }
        }
    }

    private fun sendCritMessage(player: ServerPlayerEntity, result: CheckResult) {
        if (result.isCriticalSuccess) {
            player.sendMessage(Text.literal("§6[Бросок] §a✦ КРИТИЧЕСКИЙ УСПЕХ!"), false)
        } else {
            player.sendMessage(Text.literal("§6[Бросок] §c✦ КРИТИЧЕСКИЙ ПРОВАЛ!"), false)
        }
    }

    private fun buildRollString(result: CheckResult): String {
        val dice = result.diceResult
        val parts = mutableListOf<String>()

        // Dice value
        if (dice.rolls.size == 2) {
            // Advantage/disadvantage - show both dice
            val chosen = if (result.request.advantage == omc.boundbyfate.api.dice.AdvantageType.ADVANTAGE)
                maxOf(dice.rolls[0], dice.rolls[1]) else minOf(dice.rolls[0], dice.rolls[1])
            parts.add("[${dice.rolls[0]}, ${dice.rolls[1]}]→$chosen")
        } else {
            parts.add("[${dice.rolls[0]}]")
        }

        if (result.statModifier != 0) {
            val sign = if (result.statModifier > 0) "+" else ""
            parts.add("$sign${result.statModifier}")
        }
        if (result.proficiencyBonus != 0) {
            parts.add("+${result.proficiencyBonus}(мастерство)")
        }

        return parts.joinToString(" ") + " = §f${result.total}"
    }
}
