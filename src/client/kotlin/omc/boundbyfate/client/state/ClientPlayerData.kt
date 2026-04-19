package omc.boundbyfate.client.state

import net.minecraft.util.Identifier
import omc.boundbyfate.component.EntitySkillData
import omc.boundbyfate.component.EntityStatData
import omc.boundbyfate.component.PlayerClassData
import omc.boundbyfate.component.PlayerRaceData

/**
 * Client-side cache of player character data synced from server.
 */
object ClientPlayerData {

    var statsData: EntityStatData? = null
    var skillData: EntitySkillData? = null
    var classData: PlayerClassData? = null
    var raceData: PlayerRaceData? = null
    var level: Int = 1
    var gender: String? = null
    var statBonuses: Map<Identifier, Int> = emptyMap()

    // Identity data
    var alignmentText: String = ""
    var ideals: List<ClientIdeal> = emptyList()
    var flaws: List<ClientFlaw> = emptyList()
    var motivations: List<ClientMotivation> = emptyList()

    fun clear() {
        statsData = null
        skillData = null
        classData = null
        raceData = null
        level = 1
        gender = null
        statBonuses = emptyMap()
        alignmentText = ""
        ideals = emptyList()
        flaws = emptyList()
        motivations = emptyList()
    }
}
