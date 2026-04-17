package omc.boundbyfate.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.util.Identifier
import omc.boundbyfate.api.race.RaceDefinition
import omc.boundbyfate.api.race.RaceSize
import omc.boundbyfate.api.race.SubraceDefinition
import org.slf4j.LoggerFactory
import java.io.InputStream

/**
 * Parses race and subrace definitions from JSON.
 *
 * Race format:
 * ```json
 * {
 *   "displayName": "Дварф",
 *   "size": "MEDIUM",
 *   "scaleOverride": 0.5,
 *   "speedFt": 25,
 *   "statBonuses": { "boundbyfate-core:constitution": 2 },
 *   "features": ["boundbyfate-core:darkvision_60", "boundbyfate-core:dwarven_resilience"],
 *   "subraces": ["boundbyfate-core:hill_dwarf"]
 * }
 * ```
 *
 * Subrace format — only specify fields that OVERRIDE the parent race:
 * ```json
 * {
 *   "displayName": "Горный дварф",
 *   "parentRace": "boundbyfate-core:dwarf",
 *   "statBonuses": { "boundbyfate-core:strength": 2, "boundbyfate-core:constitution": 2 },
 *   "features": ["boundbyfate-core:dwarven_armor_training"]
 * }
 * ```
 */
object RaceParser {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    fun parseRace(id: Identifier, stream: InputStream): RaceDefinition? {
        return try {
            val json = JsonParser.parseReader(stream.reader()) as? JsonObject ?: run {
                logger.error("Race $id: root must be a JSON object")
                return null
            }

            val displayName = json.get("displayName")?.asString ?: run {
                logger.error("Race $id: missing 'displayName'")
                return null
            }

            val size = json.get("size")?.asString?.let {
                runCatching { RaceSize.valueOf(it.uppercase()) }.getOrElse { RaceSize.MEDIUM }
            } ?: RaceSize.MEDIUM

            val scaleOverride = json.get("scaleOverride")?.asFloat

            val speedFt: Int = when {
                json.has("speedFt") -> json.get("speedFt").asInt
                json.has("speedMultiplier") -> (json.get("speedMultiplier").asFloat * 30).toInt()
                else -> 30
            }

            RaceDefinition(
                id = id,
                displayName = displayName,
                size = size,
                scaleOverride = scaleOverride,
                speedFt = speedFt,
                statBonuses = parseIdentifierIntMap(json, "statBonuses", id),
                // Support both "features" (new) and "abilities" (legacy)
                features = parseIdentifierList(json, "features", id)
                    .ifEmpty { parseIdentifierList(json, "abilities", id) },
                subraces = parseIdentifierList(json, "subraces", id)
            )
        } catch (e: Exception) {
            logger.error("Failed to parse race $id", e)
            null
        }
    }

    fun parseSubrace(id: Identifier, stream: InputStream): SubraceDefinition? {
        return try {
            val json = JsonParser.parseReader(stream.reader()) as? JsonObject ?: run {
                logger.error("Subrace $id: root must be a JSON object")
                return null
            }

            val displayName = json.get("displayName")?.asString ?: run {
                logger.error("Subrace $id: missing 'displayName'")
                return null
            }

            val parentRaceStr = json.get("parentRace")?.asString ?: run {
                logger.error("Subrace $id: missing 'parentRace'")
                return null
            }

            val size = json.get("size")?.asString?.let {
                runCatching { RaceSize.valueOf(it.uppercase()) }.getOrNull()
            }
            val scaleOverride = json.get("scaleOverride")?.asFloat
            val speedFt = json.get("speedFt")?.asInt
                ?: json.get("speedMultiplier")?.asFloat?.let { (it * 30).toInt() }

            val statBonuses = if (json.has("statBonuses")) parseIdentifierIntMap(json, "statBonuses", id) else null
            val features = when {
                json.has("features") -> parseIdentifierList(json, "features", id)
                json.has("abilities") -> parseIdentifierList(json, "abilities", id) // legacy
                else -> null
            }

            SubraceDefinition(
                id = id,
                displayName = displayName,
                parentRace = Identifier(parentRaceStr),
                size = size,
                scaleOverride = scaleOverride,
                speedFt = speedFt,
                statBonuses = statBonuses,
                features = features
            )
        } catch (e: Exception) {
            logger.error("Failed to parse subrace $id", e)
            null
        }
    }

    private fun parseIdentifierIntMap(obj: JsonObject, key: String, id: Identifier): Map<Identifier, Int> {
        val result = mutableMapOf<Identifier, Int>()
        obj.getAsJsonObject(key)?.entrySet()?.forEach { (k, v) ->
            try { result[Identifier(k)] = v.asInt }
            catch (e: Exception) { logger.warn("$id $key: invalid entry '$k'") }
        }
        return result
    }

    private fun parseIdentifierList(obj: JsonObject, key: String, id: Identifier): List<Identifier> {
        val result = mutableListOf<Identifier>()
        obj.getAsJsonArray(key)?.forEach { el ->
            try { result.add(Identifier(el.asString)) }
            catch (e: Exception) { logger.warn("$id $key: invalid entry '${el.asString}'") }
        }
        return result
    }
}
