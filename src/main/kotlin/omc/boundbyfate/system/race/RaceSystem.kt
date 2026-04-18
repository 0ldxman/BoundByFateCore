package omc.boundbyfate.system.race

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.api.race.RaceDefinition
import omc.boundbyfate.api.race.SubraceDefinition
import omc.boundbyfate.component.PlayerRaceData
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.RaceRegistry
import omc.boundbyfate.system.HitPointsSystem
import omc.boundbyfate.system.charclass.ClassSystem
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
        // Stat bonuses — stored as FLAT modifiers so base value stays clean
        // This allows GM to see "13 +2" (base + race bonus) separately
        if (resolved.statBonuses.isNotEmpty()) {
            val statsData = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
            if (statsData != null) {
                // Remove old modifiers from this source first (idempotent)
                var updated = statsData.withoutModifiersFrom(sourceId)
                for ((statId, bonus) in resolved.statBonuses) {
                    updated = updated.withModifier(
                        statId,
                        omc.boundbyfate.api.stat.StatModifier(
                            sourceId = sourceId,
                            type = omc.boundbyfate.api.stat.ModifierType.FLAT,
                            value = bonus
                        )
                    )
                }
                player.setAttached(BbfAttachments.ENTITY_STATS, updated)
                StatEffectProcessor.applyAll(player, updated)
            }
        }

        // Features — everything else (darkvision, resistances, proficiencies, etc.)
        // is implemented as Features and applied via FeatureSystem
        for (featureId in resolved.features) {
            omc.boundbyfate.system.feature.FeatureSystem.grantFeature(player, featureId)
        }
    }

    private fun removeRaceBonuses(
        player: ServerPlayerEntity,
        raceId: Identifier,
        subraceId: Identifier?
    ) {
        // Remove stat modifiers from this race source
        val sourceId = raceSourceId(raceId)
        val statsData = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
        if (statsData != null) {
            val updated = statsData.withoutModifiersFrom(sourceId)
            player.setAttached(BbfAttachments.ENTITY_STATS, updated)
            StatEffectProcessor.applyAll(player, updated)
        }
        // Note: features are not removed on race change to avoid breaking other sources
        // TODO: track and remove race-granted features when race changes
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

    /**
     * Синхронизирует особенности игрока с тем, что должно быть от расы, класса и подкласса.
     * Добавляет недостающие особенности с учётом текущего уровня игрока.
     * Вызывается при входе игрока или при открытии GM экрана.
     */
    fun syncFeatures(player: ServerPlayerEntity) {
        val raceData = player.getAttachedOrElse(BbfAttachments.PLAYER_RACE, null)
        val classData = player.getAttachedOrElse(BbfAttachments.PLAYER_CLASS, null)
        val featData = player.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, omc.boundbyfate.component.EntityFeatureData())

        val currentFeatures = featData.grantedFeatures
        val expectedFeatures = mutableSetOf<Identifier>()

        // Особенности от расы (и подрасы)
        if (raceData != null) {
            val raceDef = RaceRegistry.getRace(raceData.raceId)
            if (raceDef != null) {
                val subraceDef = raceData.subraceId?.let { RaceRegistry.getSubrace(it) }
                val resolved = if (subraceDef != null) {
                    subraceDef.resolve(raceDef)
                } else {
                    omc.boundbyfate.api.race.ResolvedRaceData(
                        displayName = raceDef.displayName,
                        size = raceDef.size,
                        scaleOverride = raceDef.scaleOverride,
                        speedFt = raceDef.speedFt,
                        statBonuses = raceDef.statBonuses,
                        features = raceDef.features
                    )
                }
                expectedFeatures.addAll(resolved.features)
            }
        }

        // Особенности от класса и подкласса с учётом уровня
        if (classData != null) {
            val level = classData.classLevel
            val classDef = omc.boundbyfate.registry.ClassRegistry.getClass(classData.classId)
            if (classDef != null) {
                // Все особенности класса до текущего уровня включительно
                classDef.getGrantsUpTo(level).forEach { grant ->
                    expectedFeatures.addAll(grant.features)
                }
            }
            // Особенности подкласса до текущего уровня включительно
            val subclassDef = classData.subclassId?.let {
                omc.boundbyfate.registry.ClassRegistry.getSubclass(it)
            }
            subclassDef?.getGrantsUpTo(level)?.forEach { grant ->
                expectedFeatures.addAll(grant.features)
            }
        }

        // Добавляем только недостающие
        val missingFeatures = expectedFeatures - currentFeatures
        if (missingFeatures.isNotEmpty()) {
            logger.info("Syncing ${missingFeatures.size} missing features for ${player.name.string}: $missingFeatures")
            for (featureId in missingFeatures) {
                omc.boundbyfate.system.feature.FeatureSystem.grantFeature(player, featureId)
            }
        }

        // Удаляем features которые были от расы/класса, но больше не должны быть
        // (например, при смене расы — убираем особенности старой расы)
        // Определяем "locked" features — те что должны быть от расы/класса
        val lockedBySource = expectedFeatures
        // Из текущих features убираем те, которые были locked (от расы/класса), но больше не ожидаются
        val staleLockedFeatures = currentFeatures.filter { featId ->
            // Особенность была locked (от расы/класса) если она есть в CharacterSourceResolver
            val wasLocked = omc.boundbyfate.system.CharacterSourceResolver.resolve(player).lockedFeatures.contains(featId)
            wasLocked && featId !in expectedFeatures
        }
        if (staleLockedFeatures.isNotEmpty()) {
            logger.info("Removing ${staleLockedFeatures.size} stale race/class features for ${player.name.string}: $staleLockedFeatures")
            for (featureId in staleLockedFeatures) {
                omc.boundbyfate.system.feature.FeatureSystem.removeFeature(player, featureId)
            }
        }
    }
}
