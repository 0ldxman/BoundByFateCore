package omc.boundbyfate.client.state

import net.minecraft.util.Identifier
import omc.boundbyfate.api.identity.IdealAlignment
import omc.boundbyfate.component.EntityStatData
import omc.boundbyfate.component.EntitySkillData
import omc.boundbyfate.component.PlayerClassData
import omc.boundbyfate.component.PlayerRaceData

// ── Client-side identity data structures ─────────────────────────────────────

data class ClientIdeal(
    val id: String,
    val text: String,
    val alignmentAxis: IdealAlignment,
    val isCompatible: Boolean  // pre-computed on server side
)

data class ClientFlaw(
    val id: String,
    val text: String
)

data class ClientAlignmentData(
    val lawChaos: Int,
    val goodEvil: Int
)

data class ClientMotivation(
    val id: String,
    val text: String,
    val addedByGm: Boolean,
    val isActive: Boolean
)

data class ClientProposal(
    val id: String,
    val text: String,
    val proposedBy: String
)

data class ClientGoalTask(
    val id: String,
    val description: String,
    val status: String  // TaskStatus name
)

data class ClientGoal(
    val id: String,
    val title: String,
    val description: String,
    val motivationId: String?,
    val status: String,  // GoalStatus name
    val currentTaskIndex: Int,
    val tasks: List<ClientGoalTask>
) {
    val isActive: Boolean get() = status == "ACTIVE"
    val currentTask: ClientGoalTask? get() = tasks.getOrNull(currentTaskIndex)
}

// ── GM Player Snapshot ────────────────────────────────────────────────────────

data class GmPlayerSnapshot(
    val playerName: String,
    val statsData: EntityStatData?,
    /** Stat bonuses from race/class (e.g. Dwarf +2 CON). Displayed as "13 +2" in GM screen. */
    val statBonuses: Map<Identifier, Int> = emptyMap(),
    /** Detailed breakdown: statId -> list of "sourceName|value" */
    val statBonusBreakdown: Map<Identifier, List<String>> = emptyMap(),
    val skillData: EntitySkillData?,
    /** Skill/save proficiency IDs locked by race/class — GM cannot remove these. */
    val lockedSkills: Set<Identifier> = emptySet(),
    /** Skill source names: skillId -> list of source names */
    val skillSources: Map<Identifier, List<String>> = emptyMap(),
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
    val baseSpeed: Int = 30,
    val speedModifier: Int = 0,
    val baseScale: Float = 1.0f,
    val scaleModifier: Float = 0.0f,
    val isOnline: Boolean,
    val grantedFeatures: List<Identifier> = emptyList(),
    /** Feature IDs locked by race/class — GM cannot remove these. */
    val lockedFeatures: Set<Identifier> = emptySet(),
    /** Feature source names: featureId -> source name */
    val featureSources: Map<Identifier, String> = emptyMap(),
    val vitality: Int = 5,
    val scarCount: Int = 0,
    // Identity data
    val alignmentCoords: ClientAlignmentData = ClientAlignmentData(0, 0),
    val ideals: List<ClientIdeal> = emptyList(),
    val flaws: List<ClientFlaw> = emptyList(),
    val motivations: List<ClientMotivation> = emptyList(),
    val proposals: List<ClientProposal> = emptyList(),
    val goals: List<ClientGoal> = emptyList()
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
