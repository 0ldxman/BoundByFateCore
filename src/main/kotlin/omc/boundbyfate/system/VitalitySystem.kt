package omc.boundbyfate.system

import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.component.PlayerVitalityData
import omc.boundbyfate.network.ServerPacketHandler
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfStats
import org.slf4j.LoggerFactory

/**
 * Manages the Vitality (Жизненная Сила) system.
 *
 * Called when a player dies. Rolls d20 + CON modifier to determine
 * how much vitality is lost. Vitality can only be restored through
 * deliberate actions (rest, rituals, GM intervention).
 *
 * Vitality = 0 → permanent death (handled by GM or server config).
 */
object VitalitySystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-vitality")

    /**
     * Called when a player dies. Performs the death roll and updates vitality.
     * Syncs the result to the GM.
     *
     * @return The vitality lost (0–3), or null if player had no vitality data yet
     */
    fun onPlayerDeath(player: ServerPlayerEntity): DeathRollResult {
        val current = player.getAttachedOrElse(BbfAttachments.PLAYER_VITALITY, PlayerVitalityData())

        // Roll d20
        val roll = (1..20).random()

        // Get CON modifier
        val statsData = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
        val conModifier = statsData?.getStatValue(BbfStats.CONSTITUTION.id)?.dndModifier ?: 0

        val (updated, loss) = current.applyDeathRoll(roll, conModifier)
        player.setAttached(BbfAttachments.PLAYER_VITALITY, updated)

        val result = DeathRollResult(
            roll = roll,
            conModifier = conModifier,
            total = roll + conModifier,
            vitalityLost = loss,
            newVitality = updated.vitality,
            scarGained = loss > 0,
            isPermanentDeath = updated.isDead
        )

        logger.info(
            "Death roll for ${player.name.string}: d20=$roll CON=$conModifier " +
            "total=${result.total} → -$loss vitality (now ${updated.vitality}/${PlayerVitalityData.MAX_VITALITY})" +
            if (updated.isDead) " *** PERMANENT DEATH ***" else ""
        )

        // Sync updated vitality to GM clients
        ServerPacketHandler.syncGmDataToAll(player.server)

        return result
    }

    /**
     * Restores vitality for a player (long rest, ritual, GM action).
     */
    fun restoreVitality(player: ServerPlayerEntity, amount: Int) {
        val current = player.getAttachedOrElse(BbfAttachments.PLAYER_VITALITY, PlayerVitalityData())
        val updated = current.restore(amount)
        player.setAttached(BbfAttachments.PLAYER_VITALITY, updated)
        logger.info("Restored $amount vitality for ${player.name.string} → ${updated.vitality}/${PlayerVitalityData.MAX_VITALITY}")
        ServerPacketHandler.syncGmDataToAll(player.server)
    }

    /**
     * GM override: set vitality directly.
     */
    fun setVitality(player: ServerPlayerEntity, value: Int) {
        val current = player.getAttachedOrElse(BbfAttachments.PLAYER_VITALITY, PlayerVitalityData())
        player.setAttached(BbfAttachments.PLAYER_VITALITY, current.withVitality(value))
        ServerPacketHandler.syncGmDataToAll(player.server)
    }

    /**
     * GM override: set scar count directly.
     */
    fun setScars(player: ServerPlayerEntity, value: Int) {
        val current = player.getAttachedOrElse(BbfAttachments.PLAYER_VITALITY, PlayerVitalityData())
        player.setAttached(BbfAttachments.PLAYER_VITALITY, current.withScars(value))
        ServerPacketHandler.syncGmDataToAll(player.server)
    }

    fun getVitality(player: ServerPlayerEntity): PlayerVitalityData =
        player.getAttachedOrElse(BbfAttachments.PLAYER_VITALITY, PlayerVitalityData())

    data class DeathRollResult(
        val roll: Int,
        val conModifier: Int,
        val total: Int,
        val vitalityLost: Int,
        val newVitality: Int,
        val scarGained: Boolean,
        val isPermanentDeath: Boolean
    ) {
        val outcome: String get() = when {
            isPermanentDeath -> "PERMANENT DEATH"
            vitalityLost == 0 -> "No loss (≥15)"
            vitalityLost == 1 -> "-1 Vitality (10–14)"
            vitalityLost == 2 -> "-2 Vitality (5–9)"
            else -> "-3 Vitality (<5)"
        }
    }
}
