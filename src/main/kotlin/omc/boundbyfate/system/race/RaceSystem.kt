package omc.boundbyfate.system.race

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.api.race.RaceDefinition
import omc.boundbyfate.api.race.SubraceDefinition
import omc.boundbyfate.api.skill.ProficiencyLevel
import omc.boundbyfate.component.EntitySkillData
import omc.boundbyfate.component.PlayerRaceData
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.RaceRegistry
import omc.boundbyfate.system.HitPointsSystem
import omc.boundbyfate.system.charclass.ClassSystem
import omc.boundbyfate.system.damage.DamageResistanceSystem
import omc.boundbyfate.system.proficiency.ProficiencySystem
import omc.boundbyfate.system.stat.StatEffectProcessor
import org.slf4j.LoggerFactory

/**
 * Handles applying race bonuses to players.
 *
 * Applies:
 * - Stat bonuses (added to base stats)
 * - Resistances (via DamageResistanceSystem)
 * - Proficiencies (skills and item proficiencies)
 * - Scale (via Pehkui if available)
 * - Speed modifier
 * - Abilities (stubs)
 *
 * Usage:
 * ```kotlin
 * RaceSystem.applyRace(player, Identifier("boundbyfate-core", "dwarf"), Identifier("boundbyfate-core", "hill_dwarf"))
 * ```
 */
object RaceSystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    /** Source ID used for race-based resistances */
    private fun raceSourceId(raceId: Identifier) = Identifier(raceId.namespace, "race_${raceId.path}")

    /**
     * Applies a race (and optional subrace) to a player.
     * Removes any previously applied race bonuses first.
     */
    fun applyRace(
        player: ServerPlayerEntity,
        raceId: Identifier,
        subraceId: Identifier? = null
    ) {
        val raceDef = RaceRegistry.getRace(raceId) ?: run {
            logger.warn("Unknown race ID: $raceId for player ${player.name.string}")
            return
        }

        val subraceDef = subraceId?.let {
            RaceRegistry.getSubrace(it) ?: run {
                logger.warn("Unknown subrace ID: $it for player ${player.name.string}")
                null
            }
        }

        // Remove old race bonuses if player had a race before
        val existing = player.getAttachedOrElse(BbfAttachments.PLAYER_RACE, null)
        if (existing != null) removeRaceBonuses(player, existing.raceId, existing.subraceId)

        // Save race data
        player.setAttached(BbfAttachments.PLAYER_RACE, PlayerRaceData(raceId, subraceId))

        // Apply race bonuses
        applyRaceBonuses(player, raceDef, raceSourceId(raceId))

        // Apply subrace bonuses
        if (subraceDef != null) {
            applyRaceBonuses(player, subraceDef, raceSourceId(subraceDef.id))
        }

        // Apply scale via Pehkui (if available)
        applyScale(player, raceDef.size.scaleMultiplier)

        // Apply speed modifier
        applySpeedModifier(player, raceDef.speedMultiplier)

        // Recalculate HP (CON may have changed)
        val classData = ClassSystem.getClassData(player)
        val classDef = classData?.let { omc.boundbyfate.registry.ClassRegistry.getClass(it.classId) }
        HitPointsSystem.applyHitPoints(player, classDef, classData?.classLevel ?: 1)

        logger.info("Applied race '${raceDef.displayName}' to ${player.name.string}")
    }

    /**
     * Reapplies race effects on player join (scale, speed, etc. reset on login).
     */
    fun reapplyOnJoin(player: ServerPlayerEntity) {
        val raceData = player.getAttachedOrElse(BbfAttachments.PLAYER_RACE, null) ?: return
        val raceDef = RaceRegistry.getRace(raceData.raceId) ?: return

        applyScale(player, raceDef.size.scaleMultiplier)
        applySpeedModifier(player, raceDef.speedMultiplier)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun applyRaceBonuses(
        player: ServerPlayerEntity,
        def: Any, // RaceDefinition or SubraceDefinition
        sourceId: Identifier
    ) {
        val statBonuses: Map<Identifier, Int>
        val resistances: Map<Identifier, Int>
        val proficiencies: List<Identifier>
        val itemProficiencies: List<Identifier>
        val abilities: List<Identifier>

        when (def) {
            is RaceDefinition -> {
                statBonuses = def.statBonuses
                resistances = def.resistances
                proficiencies = def.proficiencies
                itemProficiencies = def.itemProficiencies
                abilities = def.abilities
            }
            is SubraceDefinition -> {
                statBonuses = def.statBonuses
                resistances = def.resistances
                proficiencies = def.proficiencies
                itemProficiencies = def.itemProficiencies
                abilities = def.abilities
            }
            else -> return
        }

        // Stat bonuses
        if (statBonuses.isNotEmpty()) {
            val statsData = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
            if (statsData != null) {
                var updated = statsData
                for ((statId, bonus) in statBonuses) {
                    val current = updated.getStatValue(statId).base
                    updated = updated.withBase(statId, current + bonus)
                }
                player.setAttached(BbfAttachments.ENTITY_STATS, updated)
                StatEffectProcessor.applyAll(player, updated)
            }
        }

        // Resistances
        for ((damageTypeId, level) in resistances) {
            DamageResistanceSystem.addResistance(player, sourceId, damageTypeId, level)
        }

        // Skill proficiencies
        if (proficiencies.isNotEmpty()) {
            val skillData = player.getAttachedOrElse(BbfAttachments.ENTITY_SKILLS, EntitySkillData())
            var updated = skillData
            for (profId in proficiencies) {
                updated = updated.withProficiency(profId, ProficiencyLevel.PROFICIENT)
            }
            player.setAttached(BbfAttachments.ENTITY_SKILLS, updated)
        }

        // Item proficiencies
        for (profId in itemProficiencies) {
            ProficiencySystem.addProficiency(player, profId)
        }

        // Abilities (stubs)
        for (abilityId in abilities) {
            logger.debug("TODO: Apply racial ability $abilityId")
        }
    }

    private fun removeRaceBonuses(
        player: ServerPlayerEntity,
        raceId: Identifier,
        subraceId: Identifier?
    ) {
        DamageResistanceSystem.removeSource(player, raceSourceId(raceId))
        if (subraceId != null) {
            DamageResistanceSystem.removeSource(player, raceSourceId(subraceId))
        }
        // Note: stat bonuses and proficiencies are not removed on race change
        // as they may have been modified by other sources
    }

    private fun applyScale(player: ServerPlayerEntity, scale: Float) {
        if (scale == 1.0f) return
        try {
            val pehkuiClass = Class.forName("virtuoel.pehkui.api.ScaleTypes")
            val baseField = pehkuiClass.getField("BASE")
            val scaleType = baseField.get(null)
            val getMethod = scaleType.javaClass.getMethod("get", net.minecraft.entity.Entity::class.java)
            val scaleData = getMethod.invoke(scaleType, player)
            val setScaleMethod = scaleData.javaClass.getMethod("setScale", Float::class.java)
            setScaleMethod.invoke(scaleData, scale)
            logger.debug("Applied Pehkui scale $scale to ${player.name.string}")
        } catch (e: ClassNotFoundException) {
            // Pehkui not installed - silently skip
        } catch (e: Exception) {
            logger.warn("Failed to apply Pehkui scale: ${e.message}")
        }
    }

    private fun applySpeedModifier(player: ServerPlayerEntity, multiplier: Float) {
        if (multiplier == 1.0f) return
        val attribute = player.getAttributeInstance(
            net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED
        ) ?: return

        val uuid = java.util.UUID.fromString("bbf00020-0000-0000-0000-000000000001")
        attribute.getModifier(uuid)?.let { attribute.removeModifier(it) }

        val modifier = net.minecraft.entity.attribute.EntityAttributeModifier(
            uuid,
            "BoundByFate race speed",
            (multiplier - 1.0).toDouble(),
            net.minecraft.entity.attribute.EntityAttributeModifier.Operation.MULTIPLY_BASE
        )
        attribute.addPersistentModifier(modifier)
    }
}
