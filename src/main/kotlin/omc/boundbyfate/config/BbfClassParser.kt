package omc.boundbyfate.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.util.Identifier
import omc.boundbyfate.api.charclass.ClassDefinition
import omc.boundbyfate.api.charclass.LevelGrant
import omc.boundbyfate.api.charclass.SubclassDefinition
import omc.boundbyfate.registry.ClassRegistry
import org.slf4j.LoggerFactory
import java.io.InputStream

/**
 * Loads class and subclass definitions from JSON.
 *
 * JSON format for classes (data/<namespace>/bbf_class/<name>.json):
 * ```json
 * {
 *   "displayName": "Воин",
 *   "hitDie": 10,
 *   "subclassLevel": 3,
 *   "progression": {
 *     "1": {
 *       "resources": { "boundbyfate-core:action_surge": 1 },
 *       "proficiencies": ["boundbyfate-core:save_strength"],
 *       "abilities": ["boundbyfate-core:second_wind"]
 *     }
 *   }
 * }
 * ```
 *
 * JSON format for subclasses (data/<namespace>/bbf_subclass/<name>.json):
 * ```json
 * {
 *   "displayName": "Мастер боя",
 *   "parentClass": "boundbyfate-core:fighter",
 *   "progression": {
 *     "3": {
 *       "resources": { "boundbyfate-core:superiority_dice": 4 },
 *       "abilities": ["boundbyfate-core:maneuver_disarming_attack"]
 *     }
 *   }
 * }
 * ```
 */
object BbfClassParser {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    /**
     * Loads a ClassDefinition from a JSON input stream.
     *
     * @param id The identifier for this class (derived from file path)
     * @param stream The JSON input stream
     * @return ClassDefinition or null if parsing failed
     */
    fun loadClass(id: Identifier, stream: InputStream): ClassDefinition? {
        return try {
            val json = JsonParser.parseReader(stream.reader()) as? JsonObject ?: run {
                logger.error("Class $id: root element must be a JSON object")
                return null
            }

            val displayName = json.get("displayName")?.asString ?: run {
                logger.error("Class $id: missing 'displayName'")
                return null
            }

            val hitDie = json.get("hitDie")?.asInt ?: run {
                logger.error("Class $id: missing 'hitDie'")
                return null
            }

            val hpPerLevel = json.get("hpPerLevel")?.asInt ?: (hitDie / 2 + 1)
            val subclassLevel = json.get("subclassLevel")?.asInt ?: 3

            val progression = parseProgression(id, json.getAsJsonObject("progression"))

            ClassDefinition(
                id = id,
                displayName = displayName,
                hitDie = hitDie,
                hpPerLevel = hpPerLevel,
                subclassLevel = subclassLevel,
                progression = progression
            )
        } catch (e: Exception) {
            logger.error("Failed to load class $id", e)
            null
        }
    }

    /**
     * Loads a SubclassDefinition from a JSON input stream.
     */
    fun loadSubclass(id: Identifier, stream: InputStream): SubclassDefinition? {
        return try {
            val json = JsonParser.parseReader(stream.reader()) as? JsonObject ?: run {
                logger.error("Subclass $id: root element must be a JSON object")
                return null
            }

            val displayName = json.get("displayName")?.asString ?: run {
                logger.error("Subclass $id: missing 'displayName'")
                return null
            }

            val parentClassStr = json.get("parentClass")?.asString ?: run {
                logger.error("Subclass $id: missing 'parentClass'")
                return null
            }

            val parentClass = Identifier(parentClassStr)
            val progression = parseProgression(id, json.getAsJsonObject("progression"))

            SubclassDefinition(
                id = id,
                displayName = displayName,
                parentClass = parentClass,
                progression = progression
            )
        } catch (e: Exception) {
            logger.error("Failed to load subclass $id", e)
            null
        }
    }

    private fun parseProgression(id: Identifier, progressionJson: JsonObject?): Map<Int, LevelGrant> {
        if (progressionJson == null) return emptyMap()

        val result = mutableMapOf<Int, LevelGrant>()

        for ((levelStr, grantJson) in progressionJson.entrySet()) {
            val level = levelStr.toIntOrNull() ?: run {
                logger.warn("$id: invalid level key '$levelStr' in progression, skipping")
                continue
            }

            if (level !in 1..20) {
                logger.warn("$id: level $level out of range [1-20], skipping")
                continue
            }

            val grant = parseLevelGrant(id, level, grantJson)
            result[level] = grant
        }

        return result
    }

    private fun parseLevelGrant(id: Identifier, level: Int, json: JsonElement): LevelGrant {
        val obj = json as? JsonObject ?: return LevelGrant()

        // Resources: { "boundbyfate-core:rage": 2 }
        val resources = mutableMapOf<Identifier, Int>()
        obj.getAsJsonObject("resources")?.entrySet()?.forEach { (key, value) ->
            try {
                resources[Identifier(key)] = value.asInt
            } catch (e: Exception) {
                logger.warn("$id level $level: invalid resource entry '$key'")
            }
        }

        // Proficiencies: ["boundbyfate-core:athletics"]
        val proficiencies = mutableListOf<Identifier>()
        obj.getAsJsonArray("proficiencies")?.forEach { element ->
            try {
                proficiencies.add(Identifier(element.asString))
            } catch (e: Exception) {
                logger.warn("$id level $level: invalid proficiency '${element.asString}'")
            }
        }

        // Abilities: ["boundbyfate-core:second_wind"]
        val abilities = mutableListOf<Identifier>()
        obj.getAsJsonArray("abilities")?.forEach { element ->
            try {
                abilities.add(Identifier(element.asString))
            } catch (e: Exception) {
                logger.warn("$id level $level: invalid ability '${element.asString}'")
            }
        }

        return LevelGrant(
            resources = resources,
            proficiencies = proficiencies,
            abilities = abilities
        )
    }
}
