package omc.boundbyfate.client.state

import net.minecraft.util.Identifier
import omc.boundbyfate.component.EntityStatData
import omc.boundbyfate.component.EntitySkillData
import omc.boundbyfate.component.PlayerClassData
import omc.boundbyfate.component.PlayerRaceData

data class GmPlayerSnapshot(
    val playerName: String,
    val statsData: EntityStatData?,
    /** Stat bonuses from race/class (e.g. Dwarf +2 CON). Displayed as "13 +2" in GM screen. */
    val statBonuses: Map<Identifier, Int> = emptyMap(),
    val skillData: EntitySkillData?,
    /** Skill/save proficiency IDs locked by race/class — GM cannot remove these. */
    val lockedSkills: Set<Identifier> = emptySet(),
    val classData: PlayerClassData?,
    val raceData: PlayerRaceData?,
    val level: Int,
    val experience: Int = 0,
    val gender: String?,
    val alignment: String = "Neutral",
    val currentHp: Float,
    val maxHp: Float,
    val speed: Float = 0.1f,
    val scale: Float = 1.0f,
    val isOnline: Boolean,
    val grantedFeatures: List<Identifier> = emptyList(),
    /** Feature IDs locked by race/class — GM cannot remove these. */
    val lockedFeatures: Set<Identifier> = emptySet(),
    val vitality: Int = 5,
    val scarCount: Int = 0
)

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
