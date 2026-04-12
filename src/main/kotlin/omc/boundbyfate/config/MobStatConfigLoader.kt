package omc.boundbyfate.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads and caches mob bestiary profiles from JSON files.
 *
 * Config files: `world/boundbyfate/mobs/{namespace}_{path}.json`
 */
object MobStatConfigLoader {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")
    private val cache = ConcurrentHashMap<Identifier, MobStatProfile>()

    fun load(worldDirectory: Path, mobTypeId: Identifier): MobStatProfile? {
        cache[mobTypeId]?.let { return it }

        val configDir = getConfigDirectory(worldDirectory)
        val fileName = "${mobTypeId.namespace}_${mobTypeId.path}.json"
        val configFile = configDir.resolve(fileName)

        if (!Files.exists(configFile)) return null

        return try {
            val json = JsonParser.parseString(Files.readString(configFile)) as? JsonObject ?: run {
                logger.error("Mob config for '$mobTypeId': root must be a JSON object")
                return null
            }

            val profile = parseProfile(mobTypeId, json)
            cache[mobTypeId] = profile
            logger.info("Loaded mob config for '$mobTypeId' (CR ${profile.challengeRating}, AC ${profile.armorClass})")
            profile
        } catch (e: Exception) {
            logger.error("Error loading mob config for '$mobTypeId'", e)
            null
        }
    }

    private fun parseProfile(mobTypeId: Identifier, json: JsonObject): MobStatProfile {
        val cr = json.get("challengeRating")?.asFloat ?: 0f
        val xp = json.get("experienceReward")?.asInt ?: 0
        val ac = json.get("armorClass")?.asInt ?: 10

        // Base stats
        val baseStats = mutableMapOf<Identifier, Int>()
        json.getAsJsonObject("baseStats")?.entrySet()?.forEach { (key, value) ->
            try { baseStats[Identifier(key)] = value.asInt }
            catch (e: Exception) { logger.warn("$mobTypeId: invalid stat '$key'") }
        }

        // Senses
        val sensesObj = json.getAsJsonObject("senses")
        val senses = MobSenses(
            darkvision  = sensesObj?.get("darkvision")?.asInt  ?: 0,
            blindsight  = sensesObj?.get("blindsight")?.asInt  ?: 0,
            tremorsense = sensesObj?.get("tremorsense")?.asInt ?: 0,
            truesight   = sensesObj?.get("truesight")?.asInt   ?: 0
        )

        // Resistances: { "boundbyfate-core:fire": -1 }
        val resistances = mutableMapOf<Identifier, Int>()
        json.getAsJsonObject("resistances")?.entrySet()?.forEach { (key, value) ->
            try { resistances[Identifier(key)] = value.asInt }
            catch (e: Exception) { logger.warn("$mobTypeId: invalid resistance '$key'") }
        }

        // Traits
        val traits = mutableListOf<Identifier>()
        json.getAsJsonArray("traits")?.forEach { el ->
            try { traits.add(Identifier(el.asString)) }
            catch (e: Exception) { logger.warn("$mobTypeId: invalid trait '${el.asString}'") }
        }

        return MobStatProfile(
            mobTypeId = mobTypeId,
            challengeRating = cr,
            experienceReward = xp,
            armorClass = ac,
            baseStats = baseStats,
            senses = senses,
            resistances = resistances,
            traits = traits
        )
    }

    fun reload(worldDirectory: Path): Int {
        cache.clear()
        val configDir = getConfigDirectory(worldDirectory)
        if (!Files.exists(configDir)) return 0

        var count = 0
        try {
            Files.list(configDir).use { stream ->
                stream.filter { it.toString().endsWith(".json") && !it.fileName.toString().startsWith("_") }
                    .forEach { file ->
                        val fileName = file.fileName.toString().removeSuffix(".json")
                        val underscoreIdx = fileName.indexOf('_')
                        if (underscoreIdx > 0) {
                            val namespace = fileName.substring(0, underscoreIdx)
                            val path = fileName.substring(underscoreIdx + 1)
                            val mobTypeId = Identifier(namespace, path)
                            if (load(worldDirectory, mobTypeId) != null) count++
                        }
                    }
            }
        } catch (e: Exception) {
            logger.error("Error reloading mob configs", e)
        }

        logger.info("Reloaded $count mob configs")
        return count
    }

    private fun getConfigDirectory(worldDirectory: Path): Path {
        val configDir = worldDirectory.resolve("boundbyfate").resolve("mobs")
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir)
            logger.info("Created mob config directory: $configDir")
        }
        createExampleConfig(configDir)
        return configDir
    }

    private fun createExampleConfig(configDir: Path) {
        val exampleFile = configDir.resolve("_example_minecraft_zombie.json")
        if (Files.exists(exampleFile)) return

        val exampleJson = """
{
  "mobTypeId": "minecraft:zombie",
  "challengeRating": 0.25,
  "experienceReward": 50,
  "armorClass": 8,
  "baseStats": {
    "boundbyfate-core:strength": 13,
    "boundbyfate-core:constitution": 15,
    "boundbyfate-core:dexterity": 8,
    "boundbyfate-core:intelligence": 3,
    "boundbyfate-core:wisdom": 6,
    "boundbyfate-core:charisma": 5
  },
  "senses": {
    "darkvision": 60
  },
  "resistances": {
    "boundbyfate-core:necrotic": -3,
    "boundbyfate-core:poison": -3
  },
  "traits": [
    "boundbyfate-core:undead_fortitude"
  ]
}
        """.trimIndent()

        try {
            Files.writeString(exampleFile, exampleJson)
            logger.info("Created example mob config: $exampleFile")
        } catch (e: Exception) {
            logger.error("Failed to create example mob config", e)
        }
    }

    fun clearCache() = cache.clear()
}
