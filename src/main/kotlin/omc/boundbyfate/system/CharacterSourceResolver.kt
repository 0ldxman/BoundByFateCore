package omc.boundbyfate.system

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.ClassRegistry
import omc.boundbyfate.registry.RaceRegistry

/**
 * Resolves which features, skills, and stat bonuses come from immutable sources
 * (race, class, subclass) vs. manually set by the GM.
 *
 * Used by syncGmData to tell the client what is "locked" (cannot be removed)
 * and to provide tooltip breakdown of where each bonus comes from.
 */
object CharacterSourceResolver {

    /** A single bonus contribution from one source */
    data class BonusEntry(
        /** Human-readable source name, e.g. "Раса: Дварф" or "Черта: Крепкий" */
        val sourceName: String,
        val value: Int
    )

    /** A single proficiency grant from one source */
    data class ProficiencyEntry(
        /** e.g. "Класс: Воин" */
        val sourceName: String
    )

    data class LockedData(
        /** Stat bonuses from race/subrace: statId -> total bonus (for lightmap display) */
        val statBonuses: Map<Identifier, Int>,
        /** Detailed breakdown per stat: statId -> list of (sourceName, value) */
        val statBonusBreakdown: Map<Identifier, List<BonusEntry>>,
        /** Feature IDs that come from race or class (cannot be removed by GM) */
        val lockedFeatures: Set<Identifier>,
        /** Feature source names: featureId -> "Раса: Дварф" */
        val featureSources: Map<Identifier, String>,
        /** Skill/save proficiency IDs that come from race or class (cannot be removed by GM) */
        val lockedSkills: Set<Identifier>,
        /** Skill source names: skillId -> list of source names */
        val skillSources: Map<Identifier, List<ProficiencyEntry>>
    )

    fun resolve(player: ServerPlayerEntity): LockedData {
        val statBonuses = mutableMapOf<Identifier, Int>()
        val statBonusBreakdown = mutableMapOf<Identifier, MutableList<BonusEntry>>()
        val lockedFeatures = mutableSetOf<Identifier>()
        val featureSources = mutableMapOf<Identifier, String>()
        val lockedSkills = mutableSetOf<Identifier>()
        val skillSources = mutableMapOf<Identifier, MutableList<ProficiencyEntry>>()

        fun addStatBonus(statId: Identifier, bonus: Int, sourceName: String) {
            statBonuses[statId] = (statBonuses[statId] ?: 0) + bonus
            statBonusBreakdown.getOrPut(statId) { mutableListOf() }
                .add(BonusEntry(sourceName, bonus))
        }

        fun addLockedFeature(featureId: Identifier, sourceName: String) {
            lockedFeatures.add(featureId)
            featureSources[featureId] = sourceName
        }

        fun addLockedSkill(skillId: Identifier, sourceName: String) {
            lockedSkills.add(skillId)
            skillSources.getOrPut(skillId) { mutableListOf() }
                .add(ProficiencyEntry(sourceName))
        }

        // ── Race ──────────────────────────────────────────────────────────────
        val raceData = player.getAttachedOrElse(BbfAttachments.PLAYER_RACE, null)
        if (raceData != null) {
            val raceDef = RaceRegistry.getRace(raceData.raceId)
            val subraceDef = raceData.subraceId?.let { RaceRegistry.getSubrace(it) }

            if (raceDef != null) {
                val raceName = "Раса: ${raceDef.displayName}"
                // Subrace overrides statBonuses entirely if present
                val effectiveBonuses = subraceDef?.statBonuses ?: raceDef.statBonuses
                val effectiveSourceName = if (subraceDef != null) "Подраса: ${subraceDef.displayName}" else raceName
                for ((statId, bonus) in effectiveBonuses) {
                    addStatBonus(statId, bonus, effectiveSourceName)
                }
                // Features: subrace overrides race features if present
                val effectiveFeatures = subraceDef?.features ?: raceDef.features
                val featSourceName = if (subraceDef != null) "Подраса: ${subraceDef.displayName}" else raceName
                for (featureId in effectiveFeatures) {
                    addLockedFeature(featureId, featSourceName)
                }
            }
        }

        // ── Class ─────────────────────────────────────────────────────────────
        val classData = player.getAttachedOrElse(BbfAttachments.PLAYER_CLASS, null)
        if (classData != null) {
            val classDef = ClassRegistry.getClass(classData.classId)
            val subclassDef = classData.subclassId?.let { ClassRegistry.getSubclass(it) }

            if (classDef != null) {
                val className = "Класс: ${classDef.displayName}"
                for (grant in classDef.getGrantsUpTo(classData.classLevel)) {
                    for (featureId in grant.features) addLockedFeature(featureId, className)
                    for (skillId in grant.proficiencies) addLockedSkill(skillId, className)
                }
            }

            if (subclassDef != null) {
                val subclassName = "Подкласс: ${subclassDef.displayName}"
                for (grant in subclassDef.getGrantsUpTo(classData.classLevel)) {
                    for (featureId in grant.features) addLockedFeature(featureId, subclassName)
                    for (skillId in grant.proficiencies) addLockedSkill(skillId, subclassName)
                }
            }
        }

        return LockedData(
            statBonuses = statBonuses,
            statBonusBreakdown = statBonusBreakdown,
            lockedFeatures = lockedFeatures,
            featureSources = featureSources,
            lockedSkills = lockedSkills,
            skillSources = skillSources
        )
    }
}
