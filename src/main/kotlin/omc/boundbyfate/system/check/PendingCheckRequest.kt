package omc.boundbyfate.system.check

import net.minecraft.util.Identifier
import omc.boundbyfate.api.dice.AdvantageType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A pending skill check request waiting for player confirmation.
 *
 * @property id Unique request ID (used in the clickable button command)
 * @property playerName The player who must perform the check
 * @property skillId The skill to check
 * @property dc Difficulty Class (hidden from player)
 * @property advantage Advantage/disadvantage state
 * @property createdAt Timestamp for expiry cleanup
 */
data class PendingCheckRequest(
    val id: UUID,
    val playerName: String,
    val skillId: Identifier,
    val dc: Int?,
    val advantage: AdvantageType,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** True if this request is older than 10 minutes */
    val isExpired: Boolean
        get() = System.currentTimeMillis() - createdAt > 10 * 60 * 1000L
}

/**
 * In-memory store for pending check requests.
 * Requests expire after 10 minutes.
 */
object PendingCheckStore {
    private val pending = ConcurrentHashMap<UUID, PendingCheckRequest>()

    fun put(request: PendingCheckRequest) {
        // Clean up expired requests
        pending.entries.removeIf { it.value.isExpired }
        pending[request.id] = request
    }

    fun take(id: UUID): PendingCheckRequest? {
        val request = pending.remove(id) ?: return null
        return if (request.isExpired) null else request
    }

    fun getForPlayer(playerName: String): List<PendingCheckRequest> {
        return pending.values.filter { it.playerName == playerName && !it.isExpired }
    }
}
