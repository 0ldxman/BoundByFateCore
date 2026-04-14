package omc.boundbyfate.client.state

import net.minecraft.util.Identifier
import omc.boundbyfate.component.EntityStatData
import omc.boundbyfate.component.EntitySkillData
import omc.boundbyfate.component.PlayerClassData
import omc.boundbyfate.component.PlayerRaceData

/**
 * Client-side snapshot of a single player's character data for the GM screen.
 */
data class GmPlayerSnapshot(
    val playerName: String,
    val statsData: EntityStatData?,
    val skillData: EntitySkillData?,
    val classData: PlayerClassData?,
    val raceData: PlayerRaceData?,
    val level: Int,
    val gender: String?,
    val currentHp: Float,
    val maxHp: Float,
    val isOnline: Boolean
)

/**
 * Client-side GM data store — holds snapshots of all players.
 */
object ClientGmData {
    val players: MutableList<GmPlayerSnapshot> = mutableListOf()

    fun update(snapshots: List<GmPlayerSnapshot>) {
        players.clear()
        players.addAll(snapshots)
    }

    fun clear() {
        players.clear()
    }
}
