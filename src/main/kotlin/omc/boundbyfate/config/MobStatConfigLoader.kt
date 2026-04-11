package omc.boundbyfate.config

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads and caches mob stat profiles from JSON files.
 *
 * Config files are located at: `world/boundbyfate/mobs/{mobType}.json`
 * File names use underscores instead of colons (e.g., `minecraft_zombie.json`)
 */
object MobStatConfigLoader {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")
    private val cache = ConcurrentHashMap<Identifier, MobStatProfile>()
    
    /**
     * Loads a mob stat profile.
     *
     * @param worldDirectory The world directory path
     * @param mobTypeId The mob type identifier (e.g., "minecraft:zombie")
     * @return MobStatProfile or null if not found/invalid
     */
    fun load(worldDirectory: Path, mobTypeId: Identifier): MobStatProfile? {
        // Check cache first
        cache[mobTypeId]?.let { return it }
        
        // Get config directory
        val configDir = getConfigDirectory(worldDirectory)
        
        // Convert identifier to filename (minecraft:zombie -> minecraft_zombie.json)
        val fileName = "${mobTypeId.namespace}_${mobTypeId.path}.json"
        val configFile = configDir.resolve(fileName)
        
        if (!Files.exists(configFile)) {
            // Not an error - mobs without configs just don't have stats
            return null
        }
        
        return try {
            // Read and parse JSON
            val jsonString = Files.readString(configFile)
            val jsonElement = JsonParser.parseString(jsonString)
            
            // Decode using Codec
            val result = MobStatProfile.CODEC.parse(JsonOps.INSTANCE, jsonElement)
            
            result.result().ifPresent { profile ->
                // Cache and return
                cache[mobTypeId] = profile
                logger.info("Loaded mob stat config for '$mobTypeId' with ${profile.baseStats.size} stats")
            }
            
            result.error().ifPresent { error ->
                logger.error("Failed to parse mob stat config for '$mobTypeId': ${error.message()}")
            }
            
            result.result().orElse(null)
        } catch (e: Exception) {
            logger.error("Error loading mob stat config for '$mobTypeId'", e)
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
            logger.warn("Mob config directory does not exist: $configDir")
            return 0
        }
        
        var count = 0
        try {
            Files.list(configDir).use { stream ->
                stream.filter { it.toString().endsWith(".json") }
                    .forEach { file ->
                        // Parse filename back to Identifier (minecraft_zombie.json -> minecraft:zombie)
                        val fileName = file.fileName.toString().removeSuffix(".json")
                        val parts = fileName.split("_", limit = 2)
                        if (parts.size == 2) {
                            val mobTypeId = Identifier(parts[0], parts[1])
                            if (load(worldDirectory, mobTypeId) != null) {
                                count++
                            }
                        }
                    }
            }
        } catch (e: Exception) {
            logger.error("Error reloading mob stat configs", e)
        }
        
        logger.info("Reloaded $count mob stat configs")
        return count
    }
    
    /**
     * Gets the config directory, creating it if necessary.
     * Also creates an example config file if the directory is empty.
     */
    private fun getConfigDirectory(worldDirectory: Path): Path {
        val configDir = worldDirectory.resolve("boundbyfate").resolve("mobs")
        
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir)
            logger.info("Created mob config directory: $configDir")
            
            // Create example config
            createExampleConfig(configDir)
        }
        
        return configDir
    }
    
    /**
     * Creates an example mob config file.
     */
    private fun createExampleConfig(configDir: Path) {
        val exampleFile = configDir.resolve("_example_minecraft_zombie.json")
        
        if (Files.exists(exampleFile)) {
            return // Already exists
        }
        
        val exampleJson = """
{
  "mobTypeId": "minecraft:zombie",
  "baseStats": {
    "boundbyfate-core:strength": 13,
    "boundbyfate-core:constitution": 15,
    "boundbyfate-core:dexterity": 8,
    "boundbyfate-core:intelligence": 3,
    "boundbyfate-core:wisdom": 6,
    "boundbyfate-core:charisma": 5
  }
}
        """.trimIndent()
        
        try {
            Files.writeString(exampleFile, exampleJson)
            logger.info("Created example mob config: $exampleFile")
        } catch (e: Exception) {
            logger.error("Failed to create example mob config", e)
        }
    }
    
    /**
     * Clears the cache.
     */
    fun clearCache() {
        cache.clear()
    }
}
