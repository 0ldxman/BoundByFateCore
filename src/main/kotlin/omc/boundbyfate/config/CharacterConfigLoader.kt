package omc.boundbyfate.config

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads and caches character stat profiles from JSON files.
 *
 * Config files are located at: `world/boundbyfate/characters/{playerName}.json`
 */
object CharacterConfigLoader {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")
    private val cache = ConcurrentHashMap<String, CharacterStatProfile>()
    
    /**
     * Loads a character profile for a player.
     *
     * @param server The Minecraft server instance
     * @param playerName The player's username
     * @return CharacterStatProfile or null if not found/invalid
     */
    fun load(server: MinecraftServer, playerName: String): CharacterStatProfile? {
        // Check cache first
        cache[playerName]?.let { return it }
        
        // Get config directory
        val configDir = getConfigDirectory(server)
        val configFile = configDir.resolve("$playerName.json")
        
        if (!Files.exists(configFile)) {
            logger.warn("No character config found for player '$playerName' at: $configFile")
            return null
        }
        
        return try {
            // Read and parse JSON
            val jsonString = Files.readString(configFile)
            val jsonElement = JsonParser.parseString(jsonString)
            
            // Decode using Codec
            val result = CharacterStatProfile.CODEC.parse(JsonOps.INSTANCE, jsonElement)
            
            result.result().ifPresent { profile ->
                // Validate
                val errors = profile.validate()
                if (errors.isNotEmpty()) {
                    logger.error("Invalid character config for '$playerName': ${errors.joinToString(", ")}")
                    return null
                }
                
                // Cache and return
                cache[playerName] = profile
                logger.info("Loaded character config for '$playerName': race=${profile.race}, class=${profile.characterClass}, level=${profile.startingLevel}")
            }
            
            result.error().ifPresent { error ->
                logger.error("Failed to parse character config for '$playerName': ${error.message()}")
            }
            
            result.result().orElse(null)
        } catch (e: Exception) {
            logger.error("Error loading character config for '$playerName'", e)
            null
        }
    }
    
    /**
     * Reloads all cached configs from disk.
     *
     * @param server The Minecraft server instance
     * @return Number of configs reloaded
     */
    fun reload(server: MinecraftServer): Int {
        cache.clear()
        
        val configDir = getConfigDirectory(server)
        if (!Files.exists(configDir)) {
            logger.warn("Character config directory does not exist: $configDir")
            return 0
        }
        
        var count = 0
        try {
            Files.list(configDir).use { stream ->
                stream.filter { it.toString().endsWith(".json") }
                    .forEach { file ->
                        val playerName = file.fileName.toString().removeSuffix(".json")
                        if (load(server, playerName) != null) {
                            count++
                        }
                    }
            }
        } catch (e: Exception) {
            logger.error("Error reloading character configs", e)
        }
        
        logger.info("Reloaded $count character configs")
        return count
    }
    
    /**
     * Gets the config directory, creating it if necessary.
     */
    private fun getConfigDirectory(server: MinecraftServer): Path {
        val worldDir = server.getSavePath(net.minecraft.world.level.storage.LevelResource.ROOT)
        val configDir = worldDir.resolve("boundbyfate").resolve("characters")
        
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir)
            logger.info("Created character config directory: $configDir")
        }
        
        return configDir
    }
    
    /**
     * Clears the cache.
     */
    fun clearCache() {
        cache.clear()
    }
}
