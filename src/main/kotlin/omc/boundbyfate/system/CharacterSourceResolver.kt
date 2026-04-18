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
 * Used by syncGmData to tell the client what is "locked" (cannot be removed).
 */
object CharacterSourceResolver {

    data class LockedData(
        /** Stat bonuses from race/subrace: statId -> total bonus */
        val statBonuses: Map<Identifier, Int>,
        /** Feature IDs that come from race or class (cannot be removed by GM) */
        val lockedFeatures: Set<Identifier>,
        /** Skill/save proficiency IDs that come from race or class (cannot be removed by GM) */
        val lockedSkills: Set<Identifier>
    )

    fun resolve(player: ServerPlayerEntity): LockedData {
        val statBonuses = mutableMapOf<Identifier, Int>()
        val lockedFeatures = mutableSetOf<Identifier>()
        val lockedSkills = mutableSetOf<Identifier>()

        // ── Race ──────────────────────────────────────────────────────────────
        val raceData = player.getAttachedOrElse(BbfAttachments.PLAYER_RACE, null)
        if (raceData != null) {
            val raceDef = RaceRegistry.getRace(raceData.raceId)
            val subraceDef = raceData.subraceId?.let { RaceRegistry.getSubrace(it) }

            if (raceDef != null) {
                // Subrace overrides statBonuses entirely if present, otherwise use race bonuses
                val effectiveBonuses = subraceDef?.statBonuses ?: raceDef.statBonuses
                for ((statId, bonus) in effectiveBonuses) {
                    statBonuses[statId] = (statBonuses[statId] ?: 0) + bonus
                }
                // Features: subrace overrides race features if present
                val effectiveFeatures = subraceDef?.features ?: raceDef.features
                lockedFeatures.addAll(effectiveFeatures)
            }
        }

        // ── Class ─────────────────────────────────────────────────────────────
        val classData = player.getAttachedOrElse(BbfAttachments.PLAYER_CLASS, null)
        if (classData != null) {
            val classDef = ClassRegistry.getClass(classData.classId)
            val subclassDef = classData.subclassId?.let { ClassRegistry.getSubclass(it) }

            if (classDef != null) {
                val grants = classDef.getGrantsUpTo(classData.classLevel)
                for (grant in grants) {
                    lockedFeatures.addAll(grant.features)
                    lockedSkills.addAll(grant.proficiencies)
                }
            }

            if (subclassDef != null) {
                val grants = subclassDef.getGrantsUpTo(classData.classLevel)
                for (grant in grants) {
                    lockedFeatures.addAll(grant.features)
                    lockedSkills.addAll(grant.proficiencies)
                }
            }
        }

        return LockedData(statBonuses, lockedFeatures, lockedSkills)
    }
}
