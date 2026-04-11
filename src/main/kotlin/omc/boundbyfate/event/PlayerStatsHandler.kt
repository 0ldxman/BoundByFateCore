package omc.boundbyfate.event

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.component.EntityStatData
import omc.boundbyfate.config.CharacterConfigLoader
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfStats
import omc.boundbyfate.system.stat.StatEffectProcessor
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
            
            // Get world directory - use the server's save directory
            // In 1.20.1, we need to construct the path manually
            val server = player.server
            val worldDir = server.runDirectory.toPath().resolve(server.saveProperties.levelName)
            
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
            // TODO: Apply class modifiers (when class system is implemented)
            
            // Attach stats to player
            player.setAttached(BbfAttachments.ENTITY_STATS, statsData)
            
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
