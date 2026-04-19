package omc.boundbyfate.network

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.component.EntityFeatureData
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.WeaponRegistry
import omc.boundbyfate.system.feature.FeatureSystem
import org.slf4j.LoggerFactory

/**
 * Handles packets received from clients on the server side.
 * Also provides methods to send packets to clients.
 */
object ServerPacketHandler {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    fun register() {
        // Client → Server: player activates an ability from hotbar (was: USE_FEATURE)
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.USE_FEATURE) { server, player, _, buf, _ ->
            val abilityId = buf.readIdentifier()
            val hasTarget = buf.readBoolean()
            val targetUuid = if (hasTarget) buf.readUuid() else null

            server.execute {
                val target = targetUuid?.let { uuid ->
                    player.serverWorld.getEntitiesByClass(
                        net.minecraft.entity.LivingEntity::class.java,
                        player.boundingBox.expand(20.0)
                    ) { it.uuid == uuid }.firstOrNull()
                }
                // Try as ability first, fall back to feature event for backwards compat
                val ability = omc.boundbyfate.registry.AbilityRegistry.get(abilityId)
                if (ability != null) {
                    omc.boundbyfate.system.ability.AbilityActivationSystem.beginActivation(player, ability, target, null, null)
                } else {
                    // Legacy: fire as feature event
                    omc.boundbyfate.system.feature.FeatureSystem.fireEvent(player, "manual_use", mapOf("featureId" to abilityId.toString()), target)
                }
            }
        }

        // Client → Server: player updated a hotbar slot
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.UPDATE_FEATURE_SLOT) { server, player, _, buf, _ ->
            val slot = buf.readInt()
            val hasFeature = buf.readBoolean()
            val featureId = if (hasFeature) buf.readIdentifier() else null

            server.execute {
                if (slot !in 0..9) return@execute
                val data = player.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, EntityFeatureData())
                player.setAttached(BbfAttachments.ENTITY_FEATURES, data.withHotbarSlot(slot, featureId))
                logger.debug("Player ${player.name.string} set slot $slot to $featureId")
            }
        }

        // ── Ability System ────────────────────────────────────────────────────

        // Client → Server: activate an ability
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.ACTIVATE_ABILITY) { server, player, _, buf, _ ->
            val abilityId = buf.readIdentifier()
            val hasTarget = buf.readBoolean()
            val targetId = if (hasTarget) buf.readInt() else null
            val hasTargetPos = buf.readBoolean()
            val targetPos = if (hasTargetPos) {
                net.minecraft.util.math.Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble())
            } else null
            val hasUpcast = buf.readBoolean()
            val upcastLevel = if (hasUpcast) buf.readInt() else null

            server.execute {
                val ability = omc.boundbyfate.registry.AbilityRegistry.get(abilityId)
                if (ability == null) {
                    logger.warn("Player ${player.name.string} tried to activate unknown ability $abilityId")
                    return@execute
                }

                // Resolve target entity
                val target = targetId?.let { id ->
                    player.serverWorld.getEntityById(id) as? net.minecraft.entity.LivingEntity
                }

                // Validate target distance
                if (target != null) {
                    val maxRange = getMaxRange(ability.targeting)
                    if (player.distanceTo(target) > maxRange) {
                        logger.debug("Target out of range for ${player.name.string}")
                        return@execute
                    }
                }

                // Validate target position distance
                if (targetPos != null) {
                    val maxRange = getMaxRange(ability.targeting)
                    if (player.pos.distanceTo(targetPos) > maxRange) {
                        logger.debug("Target position out of range for ${player.name.string}")
                        return@execute
                    }
                }

                // Activate ability
                val result = omc.boundbyfate.system.ability.AbilityActivationSystem.beginActivation(
                    player, ability, target, targetPos, upcastLevel
                )

                logger.debug("${player.name.string} activation result: $result")
            }
        }

        // Client → Server: release charged/channeled ability
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.RELEASE_ABILITY) { server, player, _, _, _ ->
            server.execute {
                val state = player.getAttachedOrElse(BbfAttachments.ABILITY_ACTIVATION, null)
                if (state == null) {
                    logger.debug("${player.name.string} tried to release ability but no activation state")
                    return@execute
                }

                val ability = omc.boundbyfate.registry.AbilityRegistry.get(state.abilityId)
                if (ability == null) {
                    logger.error("Unknown ability ${state.abilityId} in activation state")
                    return@execute
                }

                omc.boundbyfate.system.ability.AbilityActivationSystem.completeActivation(player, ability, state)
            }
        }

        // Client → Server: cancel ability activation
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.CANCEL_ABILITY) { server, player, _, _, _ ->
            server.execute {
                omc.boundbyfate.system.ability.AbilityActivationSystem.cancelActivation(player, "Player cancelled")
            }
        }

        // Client → Server: GM adds/removes a feature
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.GM_EDIT_PLAYER_FEATURE) { server, gmPlayer, _, buf, _ ->
            if (!gmPlayer.hasPermissionLevel(2)) return@registerGlobalReceiver
            val targetName = buf.readString()
            val featureId = buf.readIdentifier()
            val add = buf.readBoolean()

            server.execute {
                val target = server.playerManager.getPlayer(targetName) ?: return@execute
                if (add) {
                    // grantFeature records the feature AND applies its effects
                    omc.boundbyfate.system.feature.FeatureSystem.grantFeature(target, featureId)
                } else {
                    // removeFeature handles cleanup (Night Vision removal, sync, etc.)
                    omc.boundbyfate.system.feature.FeatureSystem.removeFeature(target, featureId)
                }
                syncGmData(gmPlayer)
                logger.info("GM ${gmPlayer.name.string} ${if (add) "added" else "removed"} feature $featureId for $targetName")
            }
        }
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.GM_REQUEST_REFRESH) { server, player, _, _, _ ->
            if (player.hasPermissionLevel(2)) {
                server.execute { syncGmData(player) }
            }
        }
        
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.REQUEST_GM_DATA) { server, player, _, _, _ ->
            if (player.hasPermissionLevel(2)) {
                server.execute {
                    logger.info("GM ${player.name.string} requested data refresh")
                    syncGmData(player)
                }
            }
        }

        // Client → Server: GM edits a player's stats
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.GM_EDIT_PLAYER_STATS) { server, gmPlayer, _, buf, _ ->
            if (!gmPlayer.hasPermissionLevel(2)) return@registerGlobalReceiver
            val targetName = buf.readString()
            val statCount = buf.readInt()
            val newStats = mutableMapOf<net.minecraft.util.Identifier, Int>()
            repeat(statCount) { newStats[buf.readIdentifier()] = buf.readInt() }

            server.execute {
                val target = server.playerManager.getPlayer(targetName) ?: return@execute
                val statsData = target.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null) ?: return@execute
                var updated = statsData
                newStats.forEach { (id, value) -> updated = updated.withBase(id, value) }
                target.setAttached(BbfAttachments.ENTITY_STATS, updated)
                omc.boundbyfate.system.stat.StatEffectProcessor.applyAll(target, updated)
                val classData = target.getAttachedOrElse(BbfAttachments.PLAYER_CLASS, null)
                val classDef = classData?.let { omc.boundbyfate.registry.ClassRegistry.getClass(it.classId) }
                omc.boundbyfate.system.HitPointsSystem.applyHitPoints(target, classDef, classData?.classLevel ?: 1)
                syncPlayerData(target)
                syncGmData(gmPlayer)
                logger.info("GM ${gmPlayer.name.string} edited stats of $targetName")
            }
        }

        // Client → Server: GM edits class/race/level/gender
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.GM_EDIT_PLAYER_IDENTITY) { server, gmPlayer, _, buf, _ ->
            if (!gmPlayer.hasPermissionLevel(2)) return@registerGlobalReceiver
            val targetName = buf.readString()
            val hasClass = buf.readBoolean()
            val classId = if (hasClass) buf.readIdentifier() else null
            val hasSubclass = buf.readBoolean()
            val subclassId = if (hasSubclass) buf.readIdentifier() else null
            val level = buf.readInt()
            val hasRace = buf.readBoolean()
            val raceId = if (hasRace) buf.readIdentifier() else null
            val hasSubrace = buf.readBoolean()
            val subraceId = if (hasSubrace) buf.readIdentifier() else null
            val hasGender = buf.readBoolean()
            val gender = if (hasGender) buf.readString() else null

            server.execute {
                val target = server.playerManager.getPlayer(targetName) ?: return@execute
                // Apply class change
                if (classId != null) {
                    omc.boundbyfate.system.charclass.ClassSystem.applyClass(target, classId, subclassId, level)
                }
                // Apply race change — always call applyRace to properly update everything
                if (raceId != null) {
                    omc.boundbyfate.system.race.RaceSystem.applyRace(target, raceId, subraceId)
                }
                // Apply gender
                if (gender != null) {
                    target.setAttached(BbfAttachments.PLAYER_GENDER, gender)
                }
                // Update level
                val levelData = target.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, null)
                if (levelData != null) {
                    target.setAttached(BbfAttachments.PLAYER_LEVEL, levelData.copy(level = level))
                }
                syncPlayerData(target)
                syncGmData(gmPlayer)
                logger.info("GM ${gmPlayer.name.string} edited identity of $targetName")
            }
        }

        // Client → Server: GM edits skill/save proficiencies
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.GM_EDIT_PLAYER_SKILLS) { server, gmPlayer, _, buf, _ ->
            if (!gmPlayer.hasPermissionLevel(2)) return@registerGlobalReceiver
            val targetName = buf.readString()
            val count = buf.readInt()
            val proficiencies = mutableMapOf<net.minecraft.util.Identifier, Int>()
            repeat(count) { proficiencies[buf.readIdentifier()] = buf.readInt() }

            server.execute {
                val target = server.playerManager.getPlayer(targetName) ?: return@execute
                var skillData = omc.boundbyfate.component.EntitySkillData()
                proficiencies.forEach { (id, level) ->
                    if (level > 0) {
                        skillData = skillData.withProficiency(id, omc.boundbyfate.api.skill.ProficiencyLevel.fromInt(level))
                    }
                }
                target.setAttached(BbfAttachments.ENTITY_SKILLS, skillData)
                syncPlayerData(target)
                syncGmData(gmPlayer)
                logger.info("GM ${gmPlayer.name.string} edited skills of $targetName")
            }
        }

        // Client → Server: GM sets vitality/scars for a player
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.GM_EDIT_PLAYER_VITALITY) { server, gmPlayer, _, buf, _ ->
            if (!gmPlayer.hasPermissionLevel(2)) return@registerGlobalReceiver
            val targetName = buf.readString()
            val newVitality = buf.readInt()
            val newScars = buf.readInt()

            server.execute {
                val target = server.playerManager.getPlayer(targetName) ?: return@execute
                val current = target.getAttachedOrElse(BbfAttachments.PLAYER_VITALITY, omc.boundbyfate.component.PlayerVitalityData())
                target.setAttached(
                    BbfAttachments.PLAYER_VITALITY,
                    current.withVitality(newVitality).withScars(newScars)
                )
                syncGmData(gmPlayer)
                logger.info("GM ${gmPlayer.name.string} set vitality=$newVitality scars=$newScars for $targetName")
            }
        }

        // Client → Server: GM sets HP (current, max, temp)
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.GM_EDIT_PLAYER_HP) { server, gmPlayer, _, buf, _ ->
            if (!gmPlayer.hasPermissionLevel(2)) return@registerGlobalReceiver
            val targetName = buf.readString()
            val newCurrentHp = buf.readFloat()
            val newMaxHp = buf.readFloat()
            val tempHp = buf.readInt()

            server.execute {
                val target = server.playerManager.getPlayer(targetName) ?: return@execute
                // Set max HP via attribute override (same UUID as HitPointsSystem)
                val maxHpVal = newMaxHp.coerceAtLeast(1f)
                val attribute = target.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH)
                if (attribute != null) {
                    val modUuid = java.util.UUID.fromString("bbf00001-0000-0000-0000-000000000001")
                    attribute.getModifier(modUuid)?.let { attribute.removeModifier(it) }
                    attribute.baseValue = 1.0
                    attribute.addPersistentModifier(net.minecraft.entity.attribute.EntityAttributeModifier(
                        modUuid, "BoundByFate D&D HP", (maxHpVal - 1).toDouble(),
                        net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADDITION
                    ))
                }
                target.health = newCurrentHp.coerceIn(0f, maxHpVal)
                // Temp HP via absorption (Minecraft's built-in temp HP mechanism)
                if (tempHp > 0) target.absorptionAmount = tempHp.toFloat()
                syncGmData(gmPlayer)
                logger.info("GM ${gmPlayer.name.string} set HP $newCurrentHp/$newMaxHp (temp=$tempHp) for $targetName")
            }
        }

        // Client → Server: GM sets speed (ft) and scale
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.GM_EDIT_PLAYER_SPEED_SCALE) { server, gmPlayer, _, buf, _ ->
            if (!gmPlayer.hasPermissionLevel(2)) return@registerGlobalReceiver
            val targetName = buf.readString()
            val totalSpeedFt = buf.readInt()
            val totalScale = buf.readFloat()

            server.execute {
                val target = server.playerManager.getPlayer(targetName) ?: return@execute
                
                // Speed: calculate modifier based on race base
                val speedData = target.getAttachedOrElse(BbfAttachments.PLAYER_SPEED_DATA, null)
                val baseSpeedFt = speedData?.baseSpeedFt ?: 30
                val modifierFt = totalSpeedFt - baseSpeedFt
                
                val newSpeedData = omc.boundbyfate.component.PlayerSpeedData(
                    baseSpeedFt = baseSpeedFt,
                    modifierFt = modifierFt
                )
                target.setAttached(BbfAttachments.PLAYER_SPEED_DATA, newSpeedData)
                
                // Apply to attribute
                val speedAttr = target.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED)
                if (speedAttr != null) {
                    val raceModUuid = java.util.UUID.fromString("bbf00020-0000-0000-0000-000000000001")
                    speedAttr.getModifier(raceModUuid)?.let { speedAttr.removeModifier(it) }
                    speedAttr.baseValue = totalSpeedFt / 300.0
                }
                target.setAttached(BbfAttachments.PLAYER_SPEED_FT, totalSpeedFt)
                
                // Scale: calculate modifier based on race base
                val scaleData = target.getAttachedOrElse(BbfAttachments.PLAYER_SCALE_DATA, null)
                val baseScale = scaleData?.baseScale ?: 1.0f
                val modifierScale = totalScale - baseScale
                
                val newScaleData = omc.boundbyfate.component.PlayerScaleData(
                    baseScale = baseScale,
                    modifierScale = modifierScale
                )
                target.setAttached(BbfAttachments.PLAYER_SCALE_DATA, newScaleData)
                
                // Apply scale
                omc.boundbyfate.system.race.RaceSystem.applyScaleDirect(target, totalScale)
                target.setAttached(BbfAttachments.PLAYER_SCALE_OVERRIDE, totalScale)
                
                syncGmData(gmPlayer)
                logger.info("GM ${gmPlayer.name.string} set speed=${baseSpeedFt}+${modifierFt}ft scale=${baseScale}+${modifierScale} for $targetName")
            }
        }

        // Client → Server: GM sets skin by name
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.GM_SET_PLAYER_SKIN) { server, gmPlayer, _, buf, _ ->
            if (!gmPlayer.hasPermissionLevel(2)) return@registerGlobalReceiver
            val targetName = buf.readString()
            val skinName = buf.readString()
            val skinModel = buf.readString()

            server.execute {
                val target = server.playerManager.getPlayer(targetName) ?: return@execute
                val worldDir = omc.boundbyfate.util.WorldDirUtil.getWorldDir(server)
                val base64 = omc.boundbyfate.system.skin.SkinLoader.loadAsBase64(worldDir, skinName) ?: run {
                    logger.warn("GM ${gmPlayer.name.string}: skin '$skinName' not found")
                    return@execute
                }
                target.setAttached(BbfAttachments.PLAYER_SKIN, omc.boundbyfate.component.PlayerSkinData(skinName, skinModel))
                broadcastSkin(targetName, base64, skinModel, server)
                logger.info("GM ${gmPlayer.name.string} set skin=$skinName for $targetName")
            }
        }

        // Client → Server: GM edits player alignment
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.GM_EDIT_PLAYER_ALIGNMENT) { server, gmPlayer, _, buf, _ ->
            if (!gmPlayer.hasPermissionLevel(2)) return@registerGlobalReceiver
            val targetName = buf.readString()
            val mode = buf.readString() // "set" or "add"
            val lawChaos = buf.readInt()
            val goodEvil = buf.readInt()
            val reason = buf.readString()

            server.execute {
                val target = server.playerManager.getPlayer(targetName) ?: return@execute
                if (mode == "set") {
                    omc.boundbyfate.system.identity.AlignmentSystem.setAlignment(target, lawChaos, goodEvil, reason)
                } else {
                    omc.boundbyfate.system.identity.AlignmentSystem.addAlignment(target, lawChaos, goodEvil, reason)
                }
                logger.info("GM ${gmPlayer.name.string} ${mode} alignment of $targetName to ($lawChaos, $goodEvil)")
                syncGmData(gmPlayer)
            }
        }

        // Client → Server: GM adds/removes/updates an ideal for a player
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.GM_EDIT_PLAYER_IDEAL) { server, gmPlayer, _, buf, _ ->
            if (!gmPlayer.hasPermissionLevel(2)) return@registerGlobalReceiver
            val targetName = buf.readString()
            val action = buf.readString() // "add", "remove", "update"
            val id = buf.readString()
            val text = buf.readString()
            val axis = buf.readString()

            server.execute {
                val target = server.playerManager.getPlayer(targetName) ?: return@execute
                when (action) {
                    "add" -> {
                        val axisEnum = try { omc.boundbyfate.api.identity.IdealAlignment.valueOf(axis) }
                                       catch (e: Exception) { omc.boundbyfate.api.identity.IdealAlignment.ANY }
                        omc.boundbyfate.system.identity.IdealsSystem.addIdeal(target, text, axisEnum)
                    }
                    "remove" -> omc.boundbyfate.system.identity.IdealsSystem.removeIdeal(target, id)
                    "update" -> {
                        val axisEnum = try { omc.boundbyfate.api.identity.IdealAlignment.valueOf(axis) }
                                       catch (e: Exception) { null }
                        omc.boundbyfate.system.identity.IdealsSystem.updateIdeal(target, id, text.ifEmpty { null }, axisEnum)
                    }
                }
                logger.info("GM ${gmPlayer.name.string} $action ideal for $targetName")
                syncGmData(gmPlayer)
            }
        }

        // Client → Server: GM adds/removes/updates a flaw for a player
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.GM_EDIT_PLAYER_FLAW) { server, gmPlayer, _, buf, _ ->
            if (!gmPlayer.hasPermissionLevel(2)) return@registerGlobalReceiver
            val targetName = buf.readString()
            val action = buf.readString() // "add", "remove", "update"
            val id = buf.readString()
            val text = buf.readString()

            server.execute {
                val target = server.playerManager.getPlayer(targetName) ?: return@execute
                when (action) {
                    "add" -> omc.boundbyfate.system.identity.IdealsSystem.addFlaw(target, text)
                    "remove" -> omc.boundbyfate.system.identity.IdealsSystem.removeFlaw(target, id)
                    "update" -> omc.boundbyfate.system.identity.IdealsSystem.updateFlaw(target, id, text)
                }
                logger.info("GM ${gmPlayer.name.string} $action flaw for $targetName")
                syncGmData(gmPlayer)
            }
        }

        // Client → Server: GM adds/removes/updates a motivation for a player
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.GM_EDIT_PLAYER_MOTIVATION) { server, gmPlayer, _, buf, _ ->
            if (!gmPlayer.hasPermissionLevel(2)) return@registerGlobalReceiver
            val targetName = buf.readString()
            val action = buf.readString() // "add", "remove", "update"
            val id = buf.readString()
            val text = buf.readString()

            server.execute {
                val target = server.playerManager.getPlayer(targetName) ?: return@execute
                when (action) {
                    "add" -> omc.boundbyfate.system.identity.MotivationSystem.addMotivation(target, text, byGm = true)
                    "remove" -> omc.boundbyfate.system.identity.MotivationSystem.removeMotivation(target, id)
                    "update" -> omc.boundbyfate.system.identity.MotivationSystem.updateMotivation(target, id, text)
                }
                logger.info("GM ${gmPlayer.name.string} $action motivation for $targetName")
                syncGmData(gmPlayer)
            }
        }

        // Client → Server: GM accepts/rejects a motivation proposal
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.GM_HANDLE_PROPOSAL) { server, gmPlayer, _, buf, _ ->
            if (!gmPlayer.hasPermissionLevel(2)) return@registerGlobalReceiver
            val targetName = buf.readString()
            val action = buf.readString() // "accept" or "reject"
            val proposalId = buf.readString()

            server.execute {
                val target = server.playerManager.getPlayer(targetName) ?: return@execute
                when (action) {
                    "accept" -> omc.boundbyfate.system.identity.MotivationSystem.acceptProposal(target, proposalId)
                    "reject" -> omc.boundbyfate.system.identity.MotivationSystem.rejectProposal(target, proposalId)
                }
                logger.info("GM ${gmPlayer.name.string} $action proposal $proposalId for $targetName")
                syncGmData(gmPlayer)
            }
        }

        // Client → Server: GM adds/removes/updates a goal for a player
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.GM_EDIT_PLAYER_GOAL) { server, gmPlayer, _, buf, _ ->
            if (!gmPlayer.hasPermissionLevel(2)) return@registerGlobalReceiver
            // Read ALL data from buffer BEFORE server.execute to avoid IllegalReferenceCountException
            val targetName = buf.readString()
            val action = buf.readString() // "add", "remove", "complete", "fail", "task", "update", "add_task", "edit_task", "delete_task", "reorder_task"
            val goalId = buf.readString()
            val title = buf.readString()
            val description = buf.readString()
            val motivationId = buf.readString().ifEmpty { null }
            val taskStatus = buf.readString() // for "task" action or task status
            val taskCount = buf.readInt()
            // Read tasks for "add" action
            val tasks = if (action == "add") {
                (0 until taskCount).map { buf.readString() }
            } else emptyList()

            server.execute {
                val target = server.playerManager.getPlayer(targetName) ?: return@execute
                when (action) {
                    "add" -> {
                        omc.boundbyfate.system.identity.MotivationSystem.addGoal(target, title, description, motivationId, tasks)
                    }
                    "remove" -> omc.boundbyfate.system.identity.MotivationSystem.removeGoal(target, goalId)
                    "complete" -> omc.boundbyfate.system.identity.MotivationSystem.completeGoal(target, goalId, description.ifEmpty { null })
                    "fail" -> omc.boundbyfate.system.identity.MotivationSystem.failGoal(target, goalId, description.ifEmpty { null })
                    "task" -> {
                        val ts = try { omc.boundbyfate.component.TaskStatus.valueOf(taskStatus) }
                                 catch (e: Exception) { omc.boundbyfate.component.TaskStatus.COMPLETED }
                        omc.boundbyfate.system.identity.MotivationSystem.advanceTask(target, goalId, ts, description.ifEmpty { null })
                    }
                    "update" -> {
                        val status = try { omc.boundbyfate.component.GoalStatus.valueOf(taskStatus) }
                                     catch (e: Exception) { null }
                        omc.boundbyfate.system.identity.MotivationSystem.updateGoal(target, goalId, 
                            title.ifEmpty { null }, description.ifEmpty { null }, status, motivationId)
                    }
                    "add_task" -> {
                        // title = task description, description = goal desc override
                        omc.boundbyfate.system.identity.MotivationSystem.addTask(target, goalId, title, description)
                    }
                    "edit_task" -> {
                        // goalId = goalId, title = taskId, description = task description, motivationId = goal desc override, taskStatus = status
                        val taskId = title
                        val taskDesc = description
                        val goalDescOverride = motivationId
                        val status = try { omc.boundbyfate.component.TaskStatus.valueOf(taskStatus) }
                                     catch (e: Exception) { null }
                        omc.boundbyfate.system.identity.MotivationSystem.updateTask(target, goalId, taskId, 
                            taskDesc.ifEmpty { null }, goalDescOverride, status)
                    }
                    "delete_task" -> {
                        // title = taskId
                        omc.boundbyfate.system.identity.MotivationSystem.deleteTask(target, goalId, title)
                    }
                    "reorder_task" -> {
                        // title = taskId, taskCount = new order
                        omc.boundbyfate.system.identity.MotivationSystem.reorderTask(target, goalId, title, taskCount)
                    }
                }
                logger.info("GM ${gmPlayer.name.string} $action goal for $targetName")
                syncGmData(gmPlayer)
            }
        }

        // Client → Server: player proposes a motivation to GM
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.PLAYER_PROPOSE_MOTIVATION) { server, player, _, buf, _ ->
            val text = buf.readString()
            server.execute {
                omc.boundbyfate.system.identity.MotivationSystem.addProposal(player, text)
                logger.info("Player ${player.name.string} proposed motivation: $text")
                // Notify all GMs
                server.playerManager.playerList
                    .filter { it.hasPermissionLevel(2) }
                    .forEach { gm -> syncGmData(gm) }
            }
        }

        // Client → Server: GM sets complete identity data for a player (replaces all delta packets)
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.GM_SET_PLAYER_IDENTITY) { server, gmPlayer, _, buf, _ ->
            if (!gmPlayer.hasPermissionLevel(2)) return@registerGlobalReceiver
            
            // Read ALL data from buffer BEFORE server.execute to avoid IllegalReferenceCountException
            val targetName = buf.readString()
            
            // Alignment coordinates
            val lawChaos = buf.readInt()
            val goodEvil = buf.readInt()
            
            // Ideals
            val idealCount = buf.readInt()
            data class IdealData(val id: String, val text: String, val axis: String)
            val ideals = (0 until idealCount).map {
                IdealData(buf.readString(), buf.readString(), buf.readString())
            }
            
            // Flaws
            val flawCount = buf.readInt()
            data class FlawData(val id: String, val text: String)
            val flaws = (0 until flawCount).map {
                FlawData(buf.readString(), buf.readString())
            }
            
            // Motivations
            val motivationCount = buf.readInt()
            data class MotivationData(val id: String, val text: String, val addedByGm: Boolean, val isActive: Boolean)
            val motivations = (0 until motivationCount).map {
                MotivationData(buf.readString(), buf.readString(), buf.readBoolean(), buf.readBoolean())
            }
            
            // Proposals
            val proposalCount = buf.readInt()
            data class ProposalData(val id: String, val text: String, val proposedBy: String)
            val proposals = (0 until proposalCount).map {
                ProposalData(buf.readString(), buf.readString(), buf.readString())
            }
            
            // Goals
            val goalCount = buf.readInt()
            data class TaskData(val id: String, val description: String, val goalDescOverride: String, val status: String, val order: Int)
            data class GoalData(val id: String, val title: String, val description: String, val motivationId: String?, val status: String, val currentTaskIndex: Int, val tasks: List<TaskData>)
            val goals = (0 until goalCount).map {
                val goalId = buf.readString()
                val title = buf.readString()
                val description = buf.readString()
                val motivationId = buf.readString().ifEmpty { null }
                val status = buf.readString()
                val currentTaskIndex = buf.readInt()
                
                // Tasks
                val taskCount = buf.readInt()
                val tasks = (0 until taskCount).map {
                    TaskData(buf.readString(), buf.readString(), buf.readString(), buf.readString(), buf.readInt())
                }
                
                GoalData(goalId, title, description, motivationId, status, currentTaskIndex, tasks)
            }

            server.execute {
                val target = server.playerManager.getPlayer(targetName) ?: return@execute
                
                // Set alignment
                omc.boundbyfate.system.identity.AlignmentSystem.setAlignment(target, lawChaos, goodEvil, "GM edit")
                
                // Replace ideals completely
                val identityData = target.getAttachedOrCreate(BbfAttachments.PLAYER_IDENTITY)
                val newIdeals = ideals.map { ideal ->
                    val axis = try { omc.boundbyfate.api.identity.IdealAlignment.valueOf(ideal.axis) }
                               catch (e: Exception) { omc.boundbyfate.api.identity.IdealAlignment.ANY }
                    omc.boundbyfate.component.Ideal(ideal.id, ideal.text, axis)
                }
                
                // Replace flaws completely
                val newFlaws = flaws.map { flaw ->
                    omc.boundbyfate.component.Flaw(flaw.id, flaw.text)
                }
                
                // Replace motivations completely
                val newMotivations = motivations.map { mot ->
                    omc.boundbyfate.component.Motivation(mot.id, mot.text, mot.addedByGm, mot.isActive)
                }
                
                // Replace proposals completely
                val newProposals = proposals.map { prop ->
                    omc.boundbyfate.component.MotivationProposal(prop.id, prop.text, prop.proposedBy)
                }
                
                // Replace goals completely
                val newGoals = goals.map { goal ->
                    val status = try { omc.boundbyfate.component.GoalStatus.valueOf(goal.status) }
                                 catch (e: Exception) { omc.boundbyfate.component.GoalStatus.ACTIVE }
                    
                    val goalTasks = goal.tasks.map { task ->
                        val taskStatus = try { omc.boundbyfate.component.TaskStatus.valueOf(task.status) }
                                         catch (e: Exception) { omc.boundbyfate.component.TaskStatus.PENDING }
                        omc.boundbyfate.component.GoalTask(task.id, task.description, task.goalDescOverride, taskStatus, task.order)
                    }
                    
                    omc.boundbyfate.component.PersonalGoal(goal.id, goal.title, goal.description, goal.motivationId, goalTasks, goal.currentTaskIndex, status)
                }
                
                // Update identity data with new values
                val updatedIdentity = identityData.copy(
                    idealsData = identityData.idealsData.copy(ideals = newIdeals, flaws = newFlaws),
                    motivationData = omc.boundbyfate.component.PlayerMotivationData(newMotivations, newProposals, newGoals)
                )
                target.setAttached(BbfAttachments.PLAYER_IDENTITY, updatedIdentity)
                
                logger.info("GM ${gmPlayer.name.string} set complete identity data for $targetName: ${newIdeals.size} ideals, ${newFlaws.size} flaws, ${newMotivations.size} motivations, ${newGoals.size} goals")
                syncGmData(gmPlayer)
            }
        }
    }

    /**
     * Gets maximum range for a targeting component.
     */
    private fun getMaxRange(targeting: omc.boundbyfate.api.ability.component.TargetingComponent): Double {
        return targeting.range.toDouble()
    }

    /**
     * Sends current hotbar slots and granted features to a player.
     * Call on player join.
     */
    fun syncToClient(player: ServerPlayerEntity) {
        val data = player.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, EntityFeatureData())

        // Sync hotbar slots
        val slotsBuf = PacketByteBufs.create()
        for (i in 0..9) {
            val featureId = data.getHotbarSlot(i)
            slotsBuf.writeBoolean(featureId != null)
            if (featureId != null) slotsBuf.writeIdentifier(featureId)
        }
        ServerPlayNetworking.send(player, BbfPackets.SYNC_FEATURE_SLOTS, slotsBuf)

        // Sync granted features
        val featuresBuf = PacketByteBufs.create()
        featuresBuf.writeInt(data.grantedFeatures.size)
        data.grantedFeatures.forEach { featuresBuf.writeIdentifier(it) }
        ServerPlayNetworking.send(player, BbfPackets.SYNC_GRANTED_FEATURES, featuresBuf)

        // Sync weapon registry for client-side tooltips
        syncWeaponRegistry(player)

        // Sync character data (stats, skills, class, race, level)
        syncPlayerData(player)

        // Sync darkvision if player has it
        val darkvision = player.getAttachedOrElse(BbfAttachments.DARKVISION, null)
        if (darkvision != null) {
            val dvBuf = PacketByteBufs.create()
            dvBuf.writeInt(darkvision.rangeFt)
            ServerPlayNetworking.send(player, BbfPackets.SYNC_DARKVISION, dvBuf)
        }
    }

    /**
     * Sends floating attack roll text to the attacker only.
     */
    fun sendAttackRoll(
        attacker: ServerPlayerEntity,
        targetX: Double, targetY: Double, targetZ: Double,
        roll: Int, bonus: Int, hit: Boolean, isCrit: Boolean
    ) {
        val buf = PacketByteBufs.create()
        buf.writeDouble(targetX)
        buf.writeDouble(targetY)
        buf.writeDouble(targetZ)
        buf.writeInt(roll)
        buf.writeInt(bonus)
        buf.writeBoolean(hit)
        buf.writeBoolean(isCrit)
        ServerPlayNetworking.send(attacker, BbfPackets.SHOW_ATTACK_ROLL, buf)
    }

    /**
     * Sends a custom skin to all online players.
     * Called when a player joins or when admin changes skin via command.
     *
     * @param targetName The player whose skin is being set
     * @param skinBase64 Base64-encoded PNG data
     * @param skinModel "default" or "slim"
     * @param server The Minecraft server instance
     */
    fun broadcastSkin(
        targetName: String,
        skinBase64: String,
        skinModel: String,
        server: net.minecraft.server.MinecraftServer
    ) {
        val buf = PacketByteBufs.create()
        buf.writeString(targetName)
        buf.writeString(skinModel)
        buf.writeString(skinBase64)

        server.playerManager.playerList.forEach { player ->
            ServerPlayNetworking.send(player, BbfPackets.SYNC_PLAYER_SKIN, buf)
        }
    }

    /**
     * Sends all currently active custom skins to a newly joined player.
     */
    fun syncAllSkinsToPlayer(
        player: net.minecraft.server.network.ServerPlayerEntity,
        server: net.minecraft.server.MinecraftServer
    ) {
        val worldDir = omc.boundbyfate.util.WorldDirUtil.getWorldDir(server)
        server.playerManager.playerList.forEach { online ->
            val skinData = online.getAttachedOrElse(BbfAttachments.PLAYER_SKIN, null) ?: return@forEach
            val base64 = omc.boundbyfate.system.skin.SkinLoader.loadAsBase64(worldDir, skinData.skinName) ?: return@forEach
            val buf = PacketByteBufs.create()
            buf.writeString(online.name.string)
            buf.writeString(skinData.skinModel)
            buf.writeString(base64)
            ServerPlayNetworking.send(player, BbfPackets.SYNC_PLAYER_SKIN, buf)
        }
    }

    /**
     * Broadcasts skin removal to all players.
     */
    fun broadcastSkinClear(
        targetName: String,
        server: net.minecraft.server.MinecraftServer
    ) {
        val buf = PacketByteBufs.create()
        buf.writeString(targetName)
        server.playerManager.playerList.forEach { player ->
            ServerPlayNetworking.send(player, BbfPackets.CLEAR_PLAYER_SKIN, buf)
        }
    }

    // ── Ability System ────────────────────────────────────────────────────────

    /**
     * Syncs ability activation state to client (for progress bar).
     */
    fun syncAbilityActivation(player: ServerPlayerEntity) {
        val state = player.getAttachedOrElse(BbfAttachments.ABILITY_ACTIVATION, null)
        val buf = PacketByteBufs.create()
        
        if (state != null) {
            buf.writeBoolean(true)
            buf.writeIdentifier(state.abilityId)
            buf.writeFloat(state.getProgress(player.world.time))
            buf.writeString(state.activationType.name)
        } else {
            buf.writeBoolean(false)
        }
        
        ServerPlayNetworking.send(player, BbfPackets.SYNC_ABILITY_ACTIVATION, buf)
    }

    /**
     * Updates concentration status.
     */
    fun updateConcentration(player: ServerPlayerEntity) {
        val concentration = player.getAttachedOrElse(BbfAttachments.CONCENTRATION, null)
        val buf = PacketByteBufs.create()
        
        if (concentration != null) {
            buf.writeBoolean(true)
            buf.writeIdentifier(concentration.abilityId)
        } else {
            buf.writeBoolean(false)
        }
        
        ServerPlayNetworking.send(player, BbfPackets.UPDATE_CONCENTRATION, buf)
    }

    /**
     * Broadcasts ability cast to all nearby players (for visual effects).
     */
    fun broadcastAbilityCast(
        caster: net.minecraft.entity.LivingEntity,
        abilityId: Identifier,
        world: net.minecraft.server.world.ServerWorld
    ) {
        val buf = PacketByteBufs.create()
        buf.writeInt(caster.id)
        buf.writeIdentifier(abilityId)
        
        // Send to all players within 64 blocks
        world.players.forEach { player ->
            if (player.distanceTo(caster) <= 64.0) {
                ServerPlayNetworking.send(player, BbfPackets.BROADCAST_ABILITY_CAST, buf)
            }
        }
    }

    /**
     * Syncs all character data (stats, skills, class, race, level) to the client.
     */
    fun syncPlayerData(player: ServerPlayerEntity) {
        val statsData = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
        val skillData = player.getAttachedOrElse(BbfAttachments.ENTITY_SKILLS, null)
        val classData = player.getAttachedOrElse(BbfAttachments.PLAYER_CLASS, null)
        val raceData = player.getAttachedOrElse(BbfAttachments.PLAYER_RACE, null)
        val levelData = player.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, null)

        val buf = PacketByteBufs.create()

        // Stats
        val stats = statsData?.baseStats ?: emptyMap()
        buf.writeInt(stats.size)
        stats.forEach { (id, value) ->
            buf.writeIdentifier(id)
            buf.writeInt(value)
        }

        // Stat modifiers count (for now just send 0 - base stats are enough for display)
        buf.writeInt(0)

        // Skills
        val skills = skillData?.proficiencies ?: emptyMap()
        buf.writeInt(skills.size)
        skills.forEach { (id, level) ->
            buf.writeIdentifier(id)
            buf.writeInt(level)
        }

        // Class
        val hasClass = classData != null
        buf.writeBoolean(hasClass)
        if (hasClass) {
            buf.writeIdentifier(classData!!.classId)
            buf.writeInt(classData.classLevel)
            // Subclass
            val hasSubclass = classData.subclassId != null
            buf.writeBoolean(hasSubclass)
            if (hasSubclass) buf.writeIdentifier(classData.subclassId!!)
        }

        // Race
        val hasRace = raceData != null
        buf.writeBoolean(hasRace)
        if (hasRace) {
            buf.writeIdentifier(raceData!!.raceId)
        }

        // Level
        buf.writeInt(levelData?.level ?: 1)

        // Gender
        val gender = player.getAttachedOrElse(BbfAttachments.PLAYER_GENDER, null)
        buf.writeBoolean(gender != null)
        if (gender != null) buf.writeString(gender)

        ServerPlayNetworking.send(player, BbfPackets.SYNC_PLAYER_DATA, buf)
    }

    /**
     * Syncs available classes/races/skills/features to GM client.
     */
    fun syncGmRegistry(gmPlayer: ServerPlayerEntity) {
        val buf = PacketByteBufs.create()

        // Classes + subclasses
        val classes = omc.boundbyfate.registry.ClassRegistry.getAllClasses().toList()
        buf.writeInt(classes.size)
        classes.forEach { cls ->
            buf.writeIdentifier(cls.id)
            buf.writeString(cls.displayName)
            buf.writeInt(cls.subclassLevel)
            val subs = omc.boundbyfate.registry.ClassRegistry.getSubclassesFor(cls.id).toList()
            buf.writeInt(subs.size)
            subs.forEach { sub -> buf.writeIdentifier(sub.id); buf.writeString(sub.displayName) }
        }

        // Races + subraces
        val races = omc.boundbyfate.registry.RaceRegistry.getAllRaces().toList()
        buf.writeInt(races.size)
        races.forEach { race -> 
            buf.writeIdentifier(race.id)
            buf.writeString(race.displayName)
            // Get subraces for this race
            val subraces = omc.boundbyfate.registry.RaceRegistry.getSubracesFor(race.id).toList()
            buf.writeInt(subraces.size)
            subraces.forEach { subrace ->
                buf.writeIdentifier(subrace.id)
                buf.writeString(subrace.displayName)
            }
        }

        // Skills + saving throws
        val skills = omc.boundbyfate.registry.SkillRegistry.getAll().toList()
        buf.writeInt(skills.size)
        skills.forEach { skill ->
            buf.writeIdentifier(skill.id)
            buf.writeString(skill.displayName)
            buf.writeBoolean(skill.isSavingThrow)
            buf.writeIdentifier(skill.linkedStat)
        }

        // Features
        val features = omc.boundbyfate.registry.FeatureRegistry.getAllFeatures().toList()
        buf.writeInt(features.size)
        features.forEach { feat -> buf.writeIdentifier(feat.id); buf.writeString(feat.displayName) }

        ServerPlayNetworking.send(gmPlayer, BbfPackets.SYNC_GM_REGISTRY, buf)

        // Also send available skin names + base64 data for previews
        val worldDir = omc.boundbyfate.util.WorldDirUtil.getWorldDir(gmPlayer.server)
        val skins = omc.boundbyfate.system.skin.SkinLoader.listAvailableSkins(worldDir)
        val skinBuf = PacketByteBufs.create()
        skinBuf.writeInt(skins.size)
        skins.forEach { skinName ->
            skinBuf.writeString(skinName)
            val base64 = omc.boundbyfate.system.skin.SkinLoader.loadAsBase64(worldDir, skinName) ?: ""
            skinBuf.writeString(base64)
        }
        ServerPlayNetworking.send(gmPlayer, BbfPackets.SYNC_SKIN_LIST, skinBuf)
    }

    /**
     * Syncs GM data to all online players who have GM permission.
     * Called after vitality/scar changes so all GMs see updated data.
     */
    fun syncGmDataToAll(server: net.minecraft.server.MinecraftServer) {
        server.playerManager.playerList.forEach { player ->
            if (player.hasPermissionLevel(2)) syncGmData(player)
        }
    }

    /**
     * Syncs all online players' character data to a GM player.
     */
    fun syncGmData(gmPlayer: ServerPlayerEntity) {
        val server = gmPlayer.server
        val buf = PacketByteBufs.create()

        val onlinePlayers = server.playerManager.playerList
        buf.writeInt(onlinePlayers.size)

        onlinePlayers.forEach { player ->
            // Синхронизируем особенности перед отправкой данных
            omc.boundbyfate.system.race.RaceSystem.syncFeatures(player)
            
            buf.writeString(player.name.string)

            // Stats — send base values only (without race/class modifiers)
            val statsData = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
            val stats = statsData?.baseStats ?: emptyMap()
            buf.writeInt(stats.size)
            stats.forEach { (id, value) -> buf.writeIdentifier(id); buf.writeInt(value) }

            // Locked data from race/class (stat bonuses, locked features, locked skills)
            val locked = omc.boundbyfate.system.CharacterSourceResolver.resolve(player)

            // Stat bonuses from race/class
            buf.writeInt(locked.statBonuses.size)
            locked.statBonuses.forEach { (id, bonus) -> buf.writeIdentifier(id); buf.writeInt(bonus) }

            // Stat bonus breakdown: statId -> list of "sourceName|value"
            buf.writeInt(locked.statBonusBreakdown.size)
            locked.statBonusBreakdown.forEach { (statId, entries) ->
                buf.writeIdentifier(statId)
                buf.writeInt(entries.size)
                entries.forEach { entry ->
                    buf.writeString(entry.sourceName)
                    buf.writeInt(entry.value)
                }
            }

            // Skills
            val skillData = player.getAttachedOrElse(BbfAttachments.ENTITY_SKILLS, null)
            val skills = skillData?.proficiencies ?: emptyMap()
            buf.writeInt(skills.size)
            skills.forEach { (id, level) -> buf.writeIdentifier(id); buf.writeInt(level) }

            // Locked skills (from class/race)
            buf.writeInt(locked.lockedSkills.size)
            locked.lockedSkills.forEach { buf.writeIdentifier(it) }

            // Skill sources: skillId -> list of source names
            buf.writeInt(locked.skillSources.size)
            locked.skillSources.forEach { (skillId, entries) ->
                buf.writeIdentifier(skillId)
                buf.writeInt(entries.size)
                entries.forEach { buf.writeString(it.sourceName) }
            }

            // Class
            val classData = player.getAttachedOrElse(BbfAttachments.PLAYER_CLASS, null)
            buf.writeBoolean(classData != null)
            if (classData != null) {
                buf.writeIdentifier(classData.classId)
                buf.writeInt(classData.classLevel)
                buf.writeBoolean(classData.subclassId != null)
                if (classData.subclassId != null) buf.writeIdentifier(classData.subclassId!!)
            }

            // Race
            val raceData = player.getAttachedOrElse(BbfAttachments.PLAYER_RACE, null)
            buf.writeBoolean(raceData != null)
            if (raceData != null) buf.writeIdentifier(raceData.raceId)

            // Level
            val levelData = player.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, null)
            buf.writeInt(levelData?.level ?: 1)

            // Gender
            val gender = player.getAttachedOrElse(BbfAttachments.PLAYER_GENDER, null)
            buf.writeBoolean(gender != null)
            if (gender != null) buf.writeString(gender)

            // HP
            buf.writeFloat(player.health + player.absorptionAmount)
            buf.writeFloat(player.maxHealth)

            // Speed (base + modifier)
            val speedData = player.getAttachedOrElse(BbfAttachments.PLAYER_SPEED_DATA, null)
            if (speedData != null) {
                buf.writeInt(speedData.baseSpeedFt)
                buf.writeInt(speedData.modifierFt)
            } else {
                // Fallback for old data
                val storedSpeedFt = player.getAttachedOrElse(BbfAttachments.PLAYER_SPEED_FT, 0)
                val speedFt = if (storedSpeedFt > 0) storedSpeedFt else {
                    val attr = player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED)
                    (attr * 300).toInt().coerceAtLeast(5)
                }
                buf.writeInt(speedFt)
                buf.writeInt(0) // no modifier
            }

            // Scale (base + modifier)
            val scaleData = player.getAttachedOrElse(BbfAttachments.PLAYER_SCALE_DATA, null)
            if (scaleData != null) {
                buf.writeFloat(scaleData.baseScale)
                buf.writeFloat(scaleData.modifierScale)
            } else {
                // Fallback for old data
                val storedScale = player.getAttachedOrElse(BbfAttachments.PLAYER_SCALE_OVERRIDE, 0f)
                val scale = if (storedScale > 0f) storedScale else omc.boundbyfate.system.race.RaceSystem.getScaleDirect(player)
                buf.writeFloat(scale)
                buf.writeFloat(0f) // no modifier
            }

            // Experience
            buf.writeInt(player.totalExperience)

            // Alignment
            val alignment = player.getAttachedOrElse(BbfAttachments.PLAYER_ALIGNMENT, "Neutral")
            buf.writeString(alignment)

            // Granted features (all features the player has)
            val featData = player.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, null)
            val features = featData?.grantedFeatures ?: emptySet()
            buf.writeInt(features.size)
            features.forEach { buf.writeIdentifier(it) }

            // Locked features (from race/class)
            buf.writeInt(locked.lockedFeatures.size)
            locked.lockedFeatures.forEach { buf.writeIdentifier(it) }

            // Feature sources: featureId -> source name
            buf.writeInt(locked.featureSources.size)
            locked.featureSources.forEach { (featId, sourceName) ->
                buf.writeIdentifier(featId)
                buf.writeString(sourceName)
            }

            // Vitality
            val vitalityData = player.getAttachedOrElse(BbfAttachments.PLAYER_VITALITY, omc.boundbyfate.component.PlayerVitalityData())
            buf.writeInt(vitalityData.vitality)
            buf.writeInt(vitalityData.scarCount)

            // Identity: alignment coordinates
            val identityData = player.getAttachedOrCreate(BbfAttachments.PLAYER_IDENTITY)
            buf.writeInt(identityData.alignment.coordinates.lawChaos)
            buf.writeInt(identityData.alignment.coordinates.goodEvil)

            // Identity: ideals
            val currentAlignment = identityData.alignment.currentAlignment
            val ideals = identityData.idealsData.ideals
            buf.writeInt(ideals.size)
            ideals.forEach { ideal ->
                buf.writeString(ideal.id)
                buf.writeString(ideal.text)
                buf.writeString(ideal.alignmentAxis.name)
                buf.writeBoolean(ideal.isCompatibleWith(currentAlignment))
            }

            // Identity: flaws
            val flaws = identityData.idealsData.flaws
            buf.writeInt(flaws.size)
            flaws.forEach { flaw ->
                buf.writeString(flaw.id)
                buf.writeString(flaw.text)
            }

            // Identity: motivations
            val motivations = identityData.motivationData.motivations
            buf.writeInt(motivations.size)
            motivations.forEach { m ->
                buf.writeString(m.id)
                buf.writeString(m.text)
                buf.writeBoolean(m.addedByGm)
                buf.writeBoolean(m.isActive)
            }

            // Identity: proposals
            val proposals = identityData.motivationData.proposals
            buf.writeInt(proposals.size)
            proposals.forEach { p ->
                buf.writeString(p.id)
                buf.writeString(p.text)
                buf.writeString(p.proposedBy)
            }

            // Identity: goals
            val goals = identityData.motivationData.goals
            buf.writeInt(goals.size)
            logger.debug("Syncing ${goals.size} goals for ${player.name.string}")
            goals.forEach { goal ->
                buf.writeString(goal.id)
                buf.writeString(goal.title)
                buf.writeString(goal.description)
                buf.writeString(goal.motivationId ?: "")
                buf.writeString(goal.status.name)
                buf.writeInt(goal.currentTaskIndex)
                buf.writeInt(goal.tasks.size)
                logger.debug("  Goal '${goal.title}' has ${goal.tasks.size} tasks")
                goal.tasks.sortedBy { it.order }.forEach { task ->
                    buf.writeString(task.id)
                    buf.writeString(task.description)
                    buf.writeString(task.goalDescriptionOverride)
                    buf.writeString(task.status.name)
                    buf.writeInt(task.order)
                }
            }
        }

        ServerPlayNetworking.send(gmPlayer, BbfPackets.SYNC_GM_PLAYERS, buf)
    }

    private fun syncWeaponRegistry(player: ServerPlayerEntity) {
        val weapons = WeaponRegistry.getAll()
        val buf = PacketByteBufs.create()
        buf.writeInt(weapons.size)
        weapons.forEach { def ->
            buf.writeIdentifier(def.id)
            buf.writeString(def.displayName)
            buf.writeInt(def.items.size)
            def.items.forEach { buf.writeIdentifier(it) }
            buf.writeString(def.damage)
            buf.writeBoolean(def.versatileDamage != null)
            def.versatileDamage?.let { buf.writeString(it) }
            buf.writeIdentifier(def.damageType)
            buf.writeInt(def.properties.size)
            def.properties.forEach { buf.writeString(it.name) }
        }
        ServerPlayNetworking.send(player, BbfPackets.SYNC_WEAPON_REGISTRY, buf)
    }
}
