package omc.boundbyfate.system.feat

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.api.feat.FeatDefinition
import omc.boundbyfate.api.skill.ProficiencyLevel
import omc.boundbyfate.component.EntitySkillData
import omc.boundbyfate.component.EntityStatData
import omc.boundbyfate.component.PlayerFeatData
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.FeatRegistry
import omc.boundbyfate.system.HitPointsSystem
import omc.boundbyfate.system.charclass.ClassSystem
import omc.boundbyfate.system.proficiency.ProficiencySystem
import omc.boundbyfate.system.stat.StatEffectProcessor
import org.slf4j.LoggerFactory

/**
 * Handles feat application and prerequisite validation.
 *
 * Usage:
 * ```kotlin
 * // Check if player can take a feat
 * FeatSystem.canTakeFeat(player, Identifier("boundbyfate-core", "tough"))
 *
 * // Apply a feat (admin command or first-join from config)
 * FeatSystem.applyFeat(player, Identifier("boundbyfate-core", "tough"))
 *
 * // Apply ASI (+2 to one stat)
 * FeatSystem.applyStatIncrease(player, BbfStats.STRENGTH.id, 2)
 * ```
 */
object FeatSystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    /**
     * Checks if a player meets the prerequisites for a feat.
     */
    fun canTakeFeat(player: ServerPlayerEntity, featId: Identifier): Boolean {
        val feat = FeatRegistry.get(featId) ?: return false
        val prereqs = feat.prerequisites

        // Check min stats
        val statsData = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
        if (statsData != null) {
            for ((statId, minValue) in prereqs.minStats) {
                val statValue = statsData.getStatValue(statId)
                if (statValue.total < minValue) return false
            }
        }

        // Check required skill proficiencies
        val skillData = player.getAttachedOrElse(BbfAttachments.ENTITY_SKILLS, null)
        for (profId in prereqs.requiredProficiencies) {
            val level = skillData?.getProficiency(profId) ?: ProficiencyLevel.NONE
            if (level == ProficiencyLevel.NONE) return false
        }

        // Check required item proficiencies
        for (profId in prereqs.requiredItemProficiencies) {
            if (!ProficiencySystem.hasProficiency(player, profId)) return false
        }

        // Check required feats
        val featData = player.getAttachedOrElse(BbfAttachments.PLAYER_FEATS, null)
        for (requiredFeatId in prereqs.requiredFeats) {
            if (featData?.hasFeat(requiredFeatId) != true) return false
        }

        return true
    }

    /**
     * Applies a feat to a player.
     * Does NOT check prerequisites - use canTakeFeat() first if needed.
     */
    fun applyFeat(player: ServerPlayerEntity, featId: Identifier) {
        val feat = FeatRegistry.get(featId) ?: run {
            logger.warn("Unknown feat ID: $featId")
            return
        }

        // Record feat
        val featData = player.getAttachedOrElse(BbfAttachments.PLAYER_FEATS, PlayerFeatData())
        player.setAttached(BbfAttachments.PLAYER_FEATS, featData.withFeat(featId))

        // Apply stat bonuses
        if (feat.grants.statBonuses.isNotEmpty()) {
            val statsData = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
            if (statsData != null) {
                var updated = statsData
                for ((statId, bonus) in feat.grants.statBonuses) {
                    val current = updated.getStatValue(statId).base
                    updated = updated.withBase(statId, current + bonus)
                }
                player.setAttached(BbfAttachments.ENTITY_STATS, updated)
                StatEffectProcessor.applyAll(player, updated)

                // Recalculate HP if CON changed
                if (feat.grants.statBonuses.keys.any { it.path == "constitution" }) {
                    val classData = ClassSystem.getClassData(player)
                    val classDef = classData?.let {
                        omc.boundbyfate.registry.ClassRegistry.getClass(it.classId)
                    }
                    HitPointsSystem.applyHitPoints(player, classDef, classData?.classLevel ?: 1)
                }
            }
        }

        // Apply skill proficiencies
        if (feat.grants.proficiencies.isNotEmpty()) {
            val skillData = player.getAttachedOrElse(BbfAttachments.ENTITY_SKILLS, EntitySkillData())
            var updated = skillData
            for (profId in feat.grants.proficiencies) {
                updated = updated.withProficiency(profId, ProficiencyLevel.PROFICIENT)
            }
            player.setAttached(BbfAttachments.ENTITY_SKILLS, updated)
        }

        // Apply item proficiencies
        for (profId in feat.grants.itemProficiencies) {
            ProficiencySystem.addProficiency(player, profId)
        }

        // Abilities are stubs
        for (abilityId in feat.grants.abilities) {
            logger.debug("TODO: Apply ability $abilityId from feat $featId")
        }

        logger.info("Applied feat '${feat.displayName}' to ${player.name.string}")
    }

    /**
     * Applies an ASI (Ability Score Increase) to a player.
     * Used when player chooses stat increase instead of a feat.
     */
    fun applyStatIncrease(player: ServerPlayerEntity, statId: Identifier, amount: Int) {
        val statsData = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null) ?: return
        val current = statsData.getStatValue(statId).base
        val updated = statsData.withBase(statId, current + amount)
        player.setAttached(BbfAttachments.ENTITY_STATS, updated)
        StatEffectProcessor.applyAll(player, updated)

        // Record the increase
        val featData = player.getAttachedOrElse(BbfAttachments.PLAYER_FEATS, PlayerFeatData())
        player.setAttached(BbfAttachments.PLAYER_FEATS, featData.withStatIncrease(statId, amount))

        logger.info("Applied ASI +$amount to $statId for ${player.name.string}")
    }

    /**
     * Applies all feats from a character config (first join).
     */
    fun applyFeatsFromConfig(player: ServerPlayerEntity, featIds: List<Identifier>) {
        for (featId in featIds) {
            applyFeat(player, featId)
        }
    }
}
