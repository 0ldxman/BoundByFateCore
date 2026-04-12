package omc.boundbyfate.config

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
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
     * @param worldDirectory The world directory path
     * @param playerName The player's username
     * @return CharacterStatProfile or null if not found/invalid
     */
    fun load(worldDirectory: Path, playerName: String): CharacterStatProfile? {
        // Check cache first
        cache[playerName]?.let { return it }
        
        // Get config directory
        val configDir = getConfigDirectory(worldDirectory)
        val configFile = configDir.resolve("$playerName.json")
        
        if (!Files.exists(configFile)) {
            logger.warn("No character config found for player '$playerName' at: $configFile")
            logger.warn("Config dir exists: ${Files.exists(configDir)}, files in dir: ${if (Files.exists(configDir)) Files.list(configDir).map { it.fileName.toString() }.toList() else emptyList()}")
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
                } else {
                    // Cache
                    cache[playerName] = profile
                    logger.info("Loaded character config for '$playerName': race=${profile.race}, class=${profile.characterClass}, level=${profile.startingLevel}")
                }
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
     * @param worldDirectory The world directory path
     * @return Number of configs reloaded
     */
    fun reload(worldDirectory: Path): Int {
        cache.clear()
        
        val configDir = getConfigDirectory(worldDirectory)
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
                        if (load(worldDirectory, playerName) != null) {
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
     * Also creates an example config file if it doesn't exist.
     */
    private fun getConfigDirectory(worldDirectory: Path): Path {
        val configDir = worldDirectory.resolve("boundbyfate").resolve("characters")
        
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir)
            logger.info("Created character config directory: $configDir")
        }

        // Ensure skins directory exists
        val skinsDir = worldDirectory.resolve("boundbyfate").resolve("skins")
        if (!Files.exists(skinsDir)) {
            Files.createDirectories(skinsDir)
            logger.info("Created skins directory: $skinsDir")
        }
        
        // Always try to create example config (will skip if exists)
        createExampleConfig(configDir)
        
        return configDir
    }
    
    /**
     * Creates an example character config file.
     */
    private fun createExampleConfig(configDir: Path) {
        val exampleFile = configDir.resolve("_example.json")
        
        if (Files.exists(exampleFile)) {
            return // Already exists
        }
        
        val exampleJson = """
{
  "playerName": "Steve",
  "race": "boundbyfate-core:dwarf",
  "subrace": "boundbyfate-core:hill_dwarf",
  "class": "boundbyfate-core:fighter",
  "subclass": "boundbyfate-core:battle_master",
  "startingLevel": 3,
  "skin": "steve_warrior",
  "skinModel": "default",
  "baseStats": {
    "boundbyfate-core:strength": 15,
    "boundbyfate-core:constitution": 14,
    "boundbyfate-core:dexterity": 13,
    "boundbyfate-core:intelligence": 10,
    "boundbyfate-core:wisdom": 12,
    "boundbyfate-core:charisma": 8
  },
  "proficiencies": {
    "boundbyfate-core:athletics": 1,
    "boundbyfate-core:intimidation": 1,
    "boundbyfate-core:save_strength": 1,
    "boundbyfate-core:save_constitution": 1
  },
  "feats": []
}
        """.trimIndent()
        
        try {
            Files.writeString(exampleFile, exampleJson)
            logger.info("Created example character config: $exampleFile")
        } catch (e: Exception) {
            logger.error("Failed to create example config", e)
        }
    }
    
    /**
     * Clears the cache.
     */
    fun clearCache() {
        cache.clear()
    }
}
