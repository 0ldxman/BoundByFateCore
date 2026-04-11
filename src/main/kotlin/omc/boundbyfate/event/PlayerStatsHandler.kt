package omc.boundbyfate.event

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
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
                // Player already has stats - just reapply effects
                logger.info("Player '$playerName' has existing stats data - reapplying effects")
                StatEffectProcessor.applyAll(player, existingStats)
                return
            }
            
            // No existing data - this is first join or data was lost
            logger.info("Player '$playerName' has no stats data - loading from config")
            
            // Get world directory from PersistentStateManager
            // This is the most reliable way to get the actual save directory
            val serverWorld = player.serverWorld
            val stateManager = serverWorld.persistentStateManager
            
            // The persistent state manager stores data in the world directory
            // We can get the directory by accessing its internal field
            val worldDir = try {
                val directoryField = stateManager.javaClass.getDeclaredField("directory")
                directoryField.isAccessible = true
                (directoryField.get(stateManager) as java.io.File).toPath().parent
            } catch (e: Exception) {
                // Fallback: construct path manually based on server type
                logger.debug("Using fallback world directory method: ${e.message}")
                val server = player.server
                val runDir = server.runDirectory.toPath()
                if (server.isDedicated) {
                    // Dedicated server: world is in root directory
                    runDir.resolve(server.saveProperties.levelName)
                } else {
                    // Integrated server: worlds are in saves/
                    runDir.resolve("saves").resolve(server.saveProperties.levelName)
                }
            }
            
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
            
            // TODO: Apply race modifiers (when race system is implemented)
            
            // Apply class (first join only - class data not in NBT yet)
            if (profile != null) {
                val classId = profile.characterClass
                val subclassId = profile.subclass
                val classLevel = profile.startingLevel
                ClassSystem.applyClass(
                    player, classId, subclassId, classLevel
                )
            }
            
            // Attach stats to player
            player.setAttached(BbfAttachments.ENTITY_STATS, statsData)
            
            // Load skill proficiencies from config (first join only)
            val skillData = if (profile != null) {
                omc.boundbyfate.component.EntitySkillData.fromMap(profile.proficiencies)
            } else {
                omc.boundbyfate.component.EntitySkillData()
            }
            player.setAttached(BbfAttachments.ENTITY_SKILLS, skillData)
            
            // Apply all effects
            StatEffectProcessor.applyAll(player, statsData)
            
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
}
