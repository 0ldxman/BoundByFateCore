package omc.boundbyfate.config

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.server.MinecraftServer
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
     * @param server The Minecraft server instance
     * @param mobTypeId The mob type identifier (e.g., "minecraft:zombie")
     * @return MobStatProfile or null if not found/invalid
     */
    fun load(server: MinecraftServer, mobTypeId: Identifier): MobStatProfile? {
        // Check cache first
        cache[mobTypeId]?.let { return it }
        
        // Get config directory
        val configDir = getConfigDirectory(server)
        
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
     * @param server The Minecraft server instance
     * @return Number of configs reloaded
     */
    fun reload(server: MinecraftServer): Int {
        cache.clear()
        
        val configDir = getConfigDirectory(server)
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
                            if (load(server, mobTypeId) != null) {
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
     */
    private fun getConfigDirectory(server: MinecraftServer): Path {
        // Get world directory (where level.dat is located)
        val worldDir = server.runDirectory.toPath().resolve(server.session.directoryName)
        val configDir = worldDir.resolve("boundbyfate").resolve("mobs")
        
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir)
            logger.info("Created mob config directory: $configDir")
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
