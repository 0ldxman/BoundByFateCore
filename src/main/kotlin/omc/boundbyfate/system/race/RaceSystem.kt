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

        // Apply scale via Pehkui (if available) — scaleOverride takes priority over size
        val scale = raceDef.scaleOverride ?: raceDef.size.scaleMultiplier
        applyScale(player, scale)

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

        val scale = raceDef.scaleOverride ?: raceDef.size.scaleMultiplier
        // Delay scale application to ensure player is fully loaded
        scheduleDelayed(player.server, 20) {
            applyScale(player, scale)
            applySpeedModifier(player, raceDef.speedMultiplier)
        }
    }

    private fun scheduleDelayed(server: net.minecraft.server.MinecraftServer, delayTicks: Int, task: () -> Unit) {
        val wrapper = object : net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick {
            var ticks = delayTicks
            var fired = false
            override fun onEndTick(s: net.minecraft.server.MinecraftServer) {
                if (fired) return
                ticks--
                if (ticks <= 0) {
                    fired = true
                    task()
                }
            }
        }
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(wrapper)
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
        try {
            val pehkuiClass = Class.forName("virtuoel.pehkui.api.ScaleTypes")
            val baseField = pehkuiClass.getField("BASE")
            val scaleType = baseField.get(null)
            // Find get() method by name regardless of parameter type (handles obfuscation)
            val getMethod = scaleType.javaClass.methods.firstOrNull { it.name == "get" && it.parameterCount == 1 }
                ?: throw NoSuchMethodException("ScaleType.get(Entity)")
            val scaleData = getMethod.invoke(scaleType, player)
            // Try setScale first, then setTargetScale
            val setMethod = scaleData.javaClass.methods.firstOrNull {
                (it.name == "setScale" || it.name == "setTargetScale") && it.parameterCount == 1
            } ?: throw NoSuchMethodException("ScaleData.setScale/setTargetScale")
            setMethod.invoke(scaleData, scale)
            logger.info("Applied Pehkui scale $scale to ${player.name.string} via API (${setMethod.name})")
        } catch (e: ClassNotFoundException) {
            applyScaleViaCommand(player, scale)
        } catch (e: Exception) {
            logger.warn("Pehkui API failed (${e.javaClass.simpleName}: ${e.message}), trying command fallback")
            applyScaleViaCommand(player, scale)
        }
    }

    private fun applyScaleViaCommand(player: ServerPlayerEntity, scale: Float) {
        try {
            val server = player.server
            val name = player.name.string
            // Pehkui uses separate height and width scale types
            server.commandManager.executeWithPrefix(server.commandSource, "scale set pehkui:base $scale $name")
            logger.info("Applied scale $scale to $name via pehkui height/width commands")
        } catch (e: Exception) {
            logger.warn("Scale command fallback also failed: ${e.message}")
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
