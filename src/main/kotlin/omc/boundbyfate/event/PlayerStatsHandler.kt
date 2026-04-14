package omc.boundbyfate.event

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.component.EntityStatData
import omc.boundbyfate.config.CharacterConfigLoader
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfStats
import omc.boundbyfate.system.stat.StatEffectProcessor
import omc.boundbyfate.system.charclass.ClassSystem
import org.slf4j.LoggerFactory

/**
 * Handles player stat initialization and management.
 */
object PlayerStatsHandler {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")
    
    /**
     * Registers event handlers.
     */
    fun register() {
        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            onPlayerJoin(handler.player)
        }

        // Reapply HP and stats after respawn (attributes reset on death)
        ServerPlayerEvents.AFTER_RESPAWN.register { oldPlayer, newPlayer, alive ->
            onPlayerRespawn(newPlayer)
        }
    }

    /**
     * Called after player respawns. Reapplies all stats and HP.
     * Note: persistent attachments with copyOnDeath are copied automatically by Fabric,
     * but Minecraft resets EntityAttributes on respawn, so we must reapply them.
     */
    private fun onPlayerRespawn(player: ServerPlayerEntity) {
        try {
            // Give Fabric a tick to copy persistent attachments to the new player object
            // then reapply all attribute-based effects
            val statsData = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
            if (statsData == null) {
                logger.warn("No stats data on respawn for ${player.name.string} - triggering join logic")
                onPlayerJoin(player)
                return
            }

            StatEffectProcessor.applyAll(player, statsData)

            val classData = player.getAttachedOrElse(BbfAttachments.PLAYER_CLASS, null)
            val currentLevel = classData?.classLevel
                ?: player.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, null)?.level
                ?: 1
            val classDef = classData?.let { omc.boundbyfate.registry.ClassRegistry.getClass(it.classId) }
            omc.boundbyfate.system.HitPointsSystem.applyHitPoints(player, classDef, currentLevel)
            // Heal to full HP on respawn
            player.health = player.maxHealth

            // Reset scale flag so Pehkui scale gets reapplied (Pehkui resets scale on death)
            player.removeAttached(BbfAttachments.SCALE_APPLIED)
            omc.boundbyfate.system.race.RaceSystem.reapplyOnJoin(player)
            omc.boundbyfate.network.ServerPacketHandler.syncToClient(player)
            omc.boundbyfate.system.combat.ArmorClassSystem.recalculate(player)

            logger.info("Reapplied stats after respawn for ${player.name.string}")
        } catch (e: Exception) {
            logger.error("Failed to reapply stats after respawn for ${player.name.string}", e)
        }
    }
    
    /**
     * Called when a player joins the server.
     * Loads character config and applies stats.
     */
    private fun onPlayerJoin(player: ServerPlayerEntity) {
        try {
            val playerName = player.name.string
            
            // Check if player already has stats data (from NBT)
            val existingStats = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
            
            if (existingStats != null) {
                // Player already has stats - check if they're a commoner with a new config available
                val isCommoner = player.getAttachedOrElse(BbfAttachments.PLAYER_CLASS, null) == null
                val worldDir = omc.boundbyfate.util.WorldDirUtil.getWorldDir(player.server)

                if (isCommoner) {
                    // Invalidate cache so we re-read from disk
                    CharacterConfigLoader.clearCache()
                    val profile = CharacterConfigLoader.load(worldDir, playerName)
                    if (profile != null) {
                        // Config appeared — reinitialize this player from config
                        logger.info("Player '$playerName' was commoner but config now exists — reinitializing from config")
                        // Clear existing data so the full init path runs
                        player.removeAttached(BbfAttachments.ENTITY_STATS)
                        player.removeAttached(BbfAttachments.PLAYER_LEVEL)
                        player.removeAttached(BbfAttachments.ENTITY_SKILLS)
                        onPlayerJoin(player)
                        return
                    }
                }

                // Player already has stats - reapply effects and HP
                logger.info("Player '$playerName' has existing stats data - reapplying effects")
                StatEffectProcessor.applyAll(player, existingStats)
                
                // Reapply HP (attributes reset on each join)
                // Use PLAYER_LEVEL for commoners since they have no PLAYER_CLASS
                val classData = player.getAttachedOrElse(BbfAttachments.PLAYER_CLASS, null)
                val currentLevel = classData?.classLevel
                    ?: player.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, null)?.level
                    ?: 1
                if (classData != null) {
                    val classDef = omc.boundbyfate.registry.ClassRegistry.getClass(classData.classId)
                    omc.boundbyfate.system.HitPointsSystem.applyHitPoints(player, classDef, currentLevel)
                } else {
                    omc.boundbyfate.system.HitPointsSystem.applyHitPoints(player, null, currentLevel)
                }
                
                // Reapply race scale and speed (reset on each join)
                omc.boundbyfate.system.race.RaceSystem.reapplyOnJoin(player)
                
                // Sync feature slots to client
                omc.boundbyfate.network.ServerPacketHandler.syncToClient(player)

                // Broadcast this player's skin to all, and send all existing skins to this player
                // Delay by 20 ticks (1 second) to ensure client is fully connected
                val skinData = player.getAttachedOrElse(BbfAttachments.PLAYER_SKIN, null)
                if (skinData != null) {
                    val worldDir = omc.boundbyfate.util.WorldDirUtil.getWorldDir(player.server)
                    val base64 = omc.boundbyfate.system.skin.SkinLoader.loadAsBase64(worldDir, skinData.skinName)
                    if (base64 != null) {
                        val skinName = skinData.skinName
                        val skinModel = skinData.skinModel
                        scheduleDelayed(player.server, 20) {
                            omc.boundbyfate.network.ServerPacketHandler.broadcastSkin(
                                playerName, base64, skinModel, player.server
                            )
                            omc.boundbyfate.network.ServerPacketHandler.syncAllSkinsToPlayer(player, player.server)
                        }
                    }
                }
                return
            }
            
            // No existing data - this is first join or data was lost
            logger.info("Player '$playerName' has no stats data - loading from config")
            
            // Get world directory
            val worldDir = omc.boundbyfate.util.WorldDirUtil.getWorldDir(player.server)
            
            // Try to load character config
            val profile = CharacterConfigLoader.load(worldDir, playerName)
            
            val statsData = if (profile != null) {
                logger.info("Loading character '$playerName' with config")
                
                // Create stats from config
                val baseStats = profile.baseStats.toMutableMap()
                
                // Ensure all 6 core stats are present
                ensureAllStatsPresent(baseStats)
                
                EntityStatData.fromBaseStats(baseStats)
            } else {
                logger.info("Player '$playerName' has no config - using commoner defaults (all stats 10)")
                
                // Commoner: all stats 10
                val commonerStats = mapOf(
                    BbfStats.STRENGTH.id to 10,
                    BbfStats.CONSTITUTION.id to 10,
                    BbfStats.DEXTERITY.id to 10,
                    BbfStats.INTELLIGENCE.id to 10,
                    BbfStats.WISDOM.id to 10,
                    BbfStats.CHARISMA.id to 10
                )
                
                EntityStatData.fromBaseStats(commonerStats)
            }
            
            // Attach stats FIRST so HP calculation has access to CON modifier
            player.setAttached(BbfAttachments.ENTITY_STATS, statsData)

            // Initialize PLAYER_LEVEL before class application
            if (player.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, null) == null) {
                player.setAttached(
                    BbfAttachments.PLAYER_LEVEL,
                    omc.boundbyfate.component.PlayerLevelData(level = profile?.startingLevel ?: 1)
                )
            }

            // Apply race (first join only)
            if (profile != null) {
                omc.boundbyfate.system.race.RaceSystem.applyRace(
                    player, profile.race, profile.subrace
                )
            }

            // Apply class (first join only - class data not in NBT yet)
            if (profile != null) {
                val classId = profile.characterClass
                val subclassId = profile.subclass
                val classLevel = profile.startingLevel
                ClassSystem.applyClass(
                    player, classId, subclassId, classLevel
                )
                
                // Apply feats from config
                if (profile.feats.isNotEmpty()) {
                    omc.boundbyfate.system.feat.FeatSystem.applyFeatsFromConfig(player, profile.feats)
                }

                // Save gender from config
                if (profile.gender != null) {
                    player.setAttached(omc.boundbyfate.registry.BbfAttachments.PLAYER_GENDER, profile.gender)
                }

                // Apply skin from config (first join)
                if (profile.skin != null) {
                    player.setAttached(
                        BbfAttachments.PLAYER_SKIN,
                        omc.boundbyfate.component.PlayerSkinData(profile.skin, profile.skinModel)
                    )
                    val skinName = profile.skin
                    val skinModel = profile.skinModel
                    val worldDir = omc.boundbyfate.util.WorldDirUtil.getWorldDir(player.server)
                    val base64 = omc.boundbyfate.system.skin.SkinLoader.loadAsBase64(worldDir, skinName)
                    if (base64 != null) {
                        // Delay skin broadcast by 20 ticks (1 second) so the client is fully connected
                        scheduleDelayed(player.server, 20) {
                            omc.boundbyfate.network.ServerPacketHandler.broadcastSkin(
                                playerName, base64, skinModel, player.server
                            )
                            omc.boundbyfate.network.ServerPacketHandler.syncAllSkinsToPlayer(player, player.server)
                        }
                    } else {
                        logger.warn("Skin file '${profile.skin}.png' not found for player '$playerName'")
                    }
                }
            }
            
            // Load skill proficiencies from config (first join only)
            val skillData = if (profile != null) {
                omc.boundbyfate.component.EntitySkillData.fromMap(profile.proficiencies)
            } else {
                omc.boundbyfate.component.EntitySkillData()
            }
            player.setAttached(BbfAttachments.ENTITY_SKILLS, skillData)
            
            // Apply all effects (speed, etc.)
            StatEffectProcessor.applyAll(player, statsData)
            
            // Apply commoner HP if no class config
            if (profile == null) {
                omc.boundbyfate.system.HitPointsSystem.applyHitPoints(player, null, 1)
            }
            
            logger.info("Applied stats to player '$playerName'")
        } catch (e: Exception) {
            logger.error("Failed to initialize stats for player ${player.name.string}", e)
        }
    }
    
    /**
     * Ensures all 6 core stats are present in the map.
     * Missing stats are set to default value (10).
     */
    private fun ensureAllStatsPresent(stats: MutableMap<Identifier, Int>) {
        val coreStats = listOf(
            BbfStats.STRENGTH.id,
            BbfStats.CONSTITUTION.id,
            BbfStats.DEXTERITY.id,
            BbfStats.INTELLIGENCE.id,
            BbfStats.WISDOM.id,
            BbfStats.CHARISMA.id
        )
        
        for (statId in coreStats) {
            if (!stats.containsKey(statId)) {
                stats[statId] = 10 // Default value
            }
        }
    }

    /**
     * Schedules a task to run after a given number of server ticks.
     */
    private fun scheduleDelayed(server: net.minecraft.server.MinecraftServer, delayTicks: Int, task: () -> Unit) {
        var remaining = delayTicks
        val listener = net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick { _ ->
            remaining--
            if (remaining <= 0) task()
        }
        // Register a one-shot tick listener
        val wrapper = object : net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick {
            var fired = false
            var ticks = delayTicks
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
}
