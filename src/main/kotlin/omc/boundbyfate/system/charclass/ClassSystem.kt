package omc.boundbyfate.system.charclass

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.api.charclass.LevelGrant
import omc.boundbyfate.api.skill.ProficiencyLevel
import omc.boundbyfate.component.EntitySkillData
import omc.boundbyfate.component.PlayerClassData
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.ClassRegistry
import omc.boundbyfate.system.HitPointsSystem
import omc.boundbyfate.system.resource.ResourceSystem
import org.slf4j.LoggerFactory

/**
 * Handles applying class grants (resources, proficiencies, abilities) to players.
 *
 * Usage:
 * ```kotlin
 * // Apply class on first join
 * ClassSystem.applyClass(player, classId, subclassId, level)
 *
 * // Level up
 * ClassSystem.applyLevelUp(player, newLevel)
 * ```
 */
object ClassSystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    /**
     * Applies a class to a player from scratch (first join or class assignment).
     * Applies all grants from level 1 up to [level].
     */
    fun applyClass(
        player: ServerPlayerEntity,
        classId: Identifier,
        subclassId: Identifier? = null,
        level: Int = 1
    ) {
        val classDef = ClassRegistry.getClass(classId) ?: run {
            logger.warn("Unknown class ID: $classId for player ${player.name.string}")
            return
        }

        // Save class data
        val classData = PlayerClassData(classId, subclassId, level)
        player.setAttached(BbfAttachments.PLAYER_CLASS, classData)

        // Apply all grants up to current level
        val grants = classDef.getGrantsUpTo(level).toMutableList()

        // Add subclass grants if applicable
        if (subclassId != null) {
            val subclassDef = ClassRegistry.getSubclass(subclassId)
            if (subclassDef != null) {
                grants.addAll(subclassDef.getGrantsUpTo(level))
            } else {
                logger.warn("Unknown subclass ID: $subclassId for player ${player.name.string}")
            }
        }

        grants.forEach { applyGrant(player, it) }

        // Apply D&D HP based on class and level
        HitPointsSystem.applyHitPoints(player, classDef, level)

        logger.info("Applied class ${classDef.displayName} (level $level) to ${player.name.string}")
    }

    /**
     * Applies grants for a single level up.
     * Call when a player gains a class level.
     */
    fun applyLevelUp(player: ServerPlayerEntity, newLevel: Int) {
        val classData = player.getAttachedOrElse(BbfAttachments.PLAYER_CLASS, null) ?: run {
            logger.warn("Player ${player.name.string} has no class data for level up")
            return
        }

        val classDef = ClassRegistry.getClass(classData.classId) ?: return

        // Apply class grant for this level
        classDef.getGrantAt(newLevel)?.let { applyGrant(player, it) }

        // Apply subclass grant for this level
        if (classData.subclassId != null) {
            ClassRegistry.getSubclass(classData.subclassId)
                ?.getGrantAt(newLevel)
                ?.let { applyGrant(player, it) }
        }

        // Update stored level
        player.setAttached(BbfAttachments.PLAYER_CLASS, classData.copy(classLevel = newLevel))

        // Recalculate HP for new level
        HitPointsSystem.applyHitPoints(player, classDef, newLevel)

        logger.info("Applied level up to $newLevel for ${player.name.string} (${classDef.displayName})")
    }

    /**
     * Applies a single LevelGrant to a player.
     */
    private fun applyGrant(player: ServerPlayerEntity, grant: LevelGrant) {
        // Apply resources
        for ((resourceId, maximum) in grant.resources) {
            ResourceSystem.addPool(player, resourceId, maximum)
        }

        // Apply proficiencies (skill/save)
        if (grant.proficiencies.isNotEmpty()) {
            val skillData = player.getAttachedOrElse(BbfAttachments.ENTITY_SKILLS, EntitySkillData())
            var updated = skillData
            for (profId in grant.proficiencies) {
                updated = updated.withProficiency(profId, ProficiencyLevel.PROFICIENT)
            }
            player.setAttached(BbfAttachments.ENTITY_SKILLS, updated)
        }

        // Apply item/armor/tool proficiencies
        for (profId in grant.itemProficiencies) {
            omc.boundbyfate.system.proficiency.ProficiencySystem.addProficiency(player, profId)
        }

        // Features (passive properties) — grant via FeatureSystem
        for (featureId in grant.features) {
            omc.boundbyfate.system.feature.FeatureSystem.grantFeature(player, featureId)
        }

        // Abilities (active actions) — TODO: add to player's ability hotbar
        for (abilityId in grant.abilities) {
            logger.debug("Granting ability $abilityId (hotbar TODO)")
        }
    }

    /**
     * Returns the player's current class data, or null if no class assigned.
     */
    fun getClassData(player: ServerPlayerEntity): PlayerClassData? =
        player.getAttachedOrElse(BbfAttachments.PLAYER_CLASS, null)
}
