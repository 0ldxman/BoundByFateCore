package omc.boundbyfate.client.state

import net.minecraft.util.Identifier

/** Lightweight class info for GM dropdowns */
data class GmClassInfo(val id: Identifier, val displayName: String, val subclasses: List<GmSubclassInfo>)
data class GmSubclassInfo(val id: Identifier, val displayName: String)
data class GmRaceInfo(val id: Identifier, val displayName: String)
data class GmSkillInfo(val id: Identifier, val displayName: String, val isSavingThrow: Boolean)
data class GmFeatureInfo(val id: Identifier, val displayName: String)

/**
 * Client-side registry of available classes, races, skills for GM dropdowns.
 * Populated from SYNC_GM_REGISTRY packet.
 */
object ClientGmRegistry {
    val classes: MutableList<GmClassInfo> = mutableListOf()
    val races: MutableList<GmRaceInfo> = mutableListOf()
    val skills: MutableList<GmSkillInfo> = mutableListOf()
    val features: MutableList<GmFeatureInfo> = mutableListOf()

    fun update(
        classes: List<GmClassInfo>,
        races: List<GmRaceInfo>,
        skills: List<GmSkillInfo>,
        features: List<GmFeatureInfo>
    ) {
        this.classes.clear(); this.classes.addAll(classes)
        this.races.clear(); this.races.addAll(races)
        this.skills.clear(); this.skills.addAll(skills)
        this.features.clear(); this.features.addAll(features)
    }
}
