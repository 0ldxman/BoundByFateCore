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

        // Resolve effective race data — subrace overrides race fields
        val resolved = if (subraceDef != null) {
            subraceDef.resolve(raceDef)
        } else {
            omc.boundbyfate.api.race.ResolvedRaceData(
                displayName = raceDef.displayName,
                size = raceDef.size,
                scaleOverride = raceDef.scaleOverride,
                speedFt = raceDef.speedFt,
                statBonuses = raceDef.statBonuses,
                senses = raceDef.senses,
                resistances = raceDef.resistances,
                proficiencies = raceDef.proficiencies,
                itemProficiencies = raceDef.itemProficiencies,
                features = raceDef.features
            )
        }

        // Apply resolved bonuses
        applyResolvedBonuses(player, resolved, raceSourceId(raceId))

        // Apply scale
        val scale = resolved.scaleOverride ?: resolved.size.scaleMultiplier
        applyScale(player, scale)

        // Apply speed
        applySpeedFt(player, resolved.speedFt)

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
        val subraceDef = raceData.subraceId?.let { RaceRegistry.getSubrace(it) }

        val resolved = if (subraceDef != null) subraceDef.resolve(raceDef) else
            omc.boundbyfate.api.race.ResolvedRaceData(
                displayName = raceDef.displayName,
                size = raceDef.size,
                scaleOverride = raceDef.scaleOverride,
                speedFt = raceDef.speedFt,
                statBonuses = raceDef.statBonuses,
                senses = raceDef.senses,
                resistances = raceDef.resistances,
                proficiencies = raceDef.proficiencies,
                itemProficiencies = raceDef.itemProficiencies,
                features = raceDef.features
            )

        val scale = resolved.scaleOverride ?: resolved.size.scaleMultiplier
        val scaleApplied = player.getAttachedOrElse(BbfAttachments.SCALE_APPLIED, false)
        if (!scaleApplied) {
            player.setAttached(BbfAttachments.SCALE_APPLIED, true)
            pendingScaleTasks[player.uuid] = Pair(player.server.ticks + 20, {
                applyScale(player, scale)
                applySpeedFt(player, resolved.speedFt)
            })
        } else {
            applySpeedFt(player, resolved.speedFt)
        }
    }

    /** Call this from the server tick event in BoundByFateCore */
    fun tickPendingScales(server: net.minecraft.server.MinecraftServer) {
        val currentTick = server.ticks
        val iter = pendingScaleTasks.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (currentTick >= entry.value.first) {
                entry.value.second.invoke()
                iter.remove()
            }
        }
    }

    private val pendingScaleTasks = java.util.concurrent.ConcurrentHashMap<java.util.UUID, Pair<Int, () -> Unit>>()

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

    private fun applyResolvedBonuses(
        player: ServerPlayerEntity,
        resolved: omc.boundbyfate.api.race.ResolvedRaceData,
        sourceId: Identifier
    ) {
        // Stat bonuses
        if (resolved.statBonuses.isNotEmpty()) {
            val statsData = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
            if (statsData != null) {
                var updated = statsData
                for ((statId, bonus) in resolved.statBonuses) {
                    val current = updated.getStatValue(statId).base
                    updated = updated.withBase(statId, current + bonus)
                }
                player.setAttached(BbfAttachments.ENTITY_STATS, updated)
                StatEffectProcessor.applyAll(player, updated)
            }
        }

        // Resistances
        for ((damageTypeId, level) in resolved.resistances) {
            DamageResistanceSystem.addResistance(player, sourceId, damageTypeId, level)
        }

        // Skill proficiencies
        if (resolved.proficiencies.isNotEmpty()) {
            val skillData = player.getAttachedOrElse(BbfAttachments.ENTITY_SKILLS, EntitySkillData())
            var updated = skillData
            for (profId in resolved.proficiencies) {
                updated = updated.withProficiency(profId, ProficiencyLevel.PROFICIENT)
            }
            player.setAttached(BbfAttachments.ENTITY_SKILLS, updated)
        }

        // Item proficiencies
        for (profId in resolved.itemProficiencies) {
            ProficiencySystem.addProficiency(player, profId)
        }

        // Features (passive properties)
        for (featureId in resolved.features) {
            omc.boundbyfate.system.feature.FeatureSystem.grantFeature(player, featureId)
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
        applyScaleDirect(player, scale)
    }

    /**
     * Public: directly set player scale (used by GM override, not tied to race).
     */
    fun applyScaleDirect(player: ServerPlayerEntity, scale: Float) {        try {
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
            server.commandManager.executeWithPrefix(server.commandSource, "scale set pehkui:height $scale $name")
            server.commandManager.executeWithPrefix(server.commandSource, "scale set pehkui:width $scale $name")
            logger.info("Applied scale $scale to $name via scale commands")
        } catch (e: Exception) {
            logger.warn("Scale command fallback also failed: ${e.message}")
        }
    }

    /**
     * Public: read current scale for a player.
     * Reads from RaceDefinition directly (most reliable) rather than Pehkui API.
     */
    fun getScaleDirect(player: ServerPlayerEntity): Float {
        val raceData = player.getAttachedOrElse(BbfAttachments.PLAYER_RACE, null)
        if (raceData != null) {
            val raceDef = RaceRegistry.getRace(raceData.raceId)
            if (raceDef != null) {
                val subraceDef = raceData.subraceId?.let { RaceRegistry.getSubrace(it) }
                val scaleOverride = subraceDef?.scaleOverride ?: raceDef.scaleOverride
                val size = subraceDef?.size ?: raceDef.size
                return scaleOverride ?: size.scaleMultiplier
            }
        }
        // Fallback: try Pehkui API
        return try {
            val pehkuiClass = Class.forName("virtuoel.pehkui.api.ScaleTypes")
            val baseField = pehkuiClass.getField("BASE")
            val scaleType = baseField.get(null)
            val getMethod = scaleType.javaClass.methods.firstOrNull { it.name == "get" && it.parameterCount == 1 }
                ?: return 1.0f
            val scaleData = getMethod.invoke(scaleType, player)
            // Try various getter names
            val getScaleMethod = scaleData.javaClass.methods.firstOrNull { m ->
                (m.name == "getScale" || m.name == "getTargetScale") && m.parameterCount == 0
            } ?: return 1.0f
            (getScaleMethod.invoke(scaleData) as? Float) ?: 1.0f
        } catch (e: Exception) {
            1.0f
        }
    }

    private fun applySpeedFt(player: ServerPlayerEntity, speedFt: Int) {
        val attribute = player.getAttributeInstance(
            net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED
        ) ?: return
        // Remove any existing BBF race speed modifier
        val uuid = java.util.UUID.fromString("bbf00020-0000-0000-0000-000000000001")
        attribute.getModifier(uuid)?.let { attribute.removeModifier(it) }
        // Set base value directly: 30ft = 0.1, formula: speedFt / 300.0
        attribute.baseValue = speedFt / 300.0
        // Store for GM panel readback
        player.setAttached(BbfAttachments.PLAYER_SPEED_FT, speedFt)
    }
}
