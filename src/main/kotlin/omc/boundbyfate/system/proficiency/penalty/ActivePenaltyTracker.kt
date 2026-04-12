package omc.boundbyfate.system.proficiency.penalty

import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks active per-tick penalties for players.
 * Used by mixins to read penalty values during attack/interaction.
 *
 * Values are set each time the player holds an item without proficiency
 * and cleared when they switch to a proficient item.
 */
object ActivePenaltyTracker {
    private val missChances = ConcurrentHashMap<UUID, Float>()

    fun setMissChance(player: ServerPlayerEntity, chance: Float) {
        missChances[player.uuid] = chance
    }

    fun getMissChance(player: ServerPlayerEntity): Float {
        return missChances[player.uuid] ?: 0.0f
    }

    fun clearMissChance(player: ServerPlayerEntity) {
        missChances.remove(player.uuid)
    }

    fun clearAll(player: ServerPlayerEntity) {
        missChances.remove(player.uuid)
    }
}
