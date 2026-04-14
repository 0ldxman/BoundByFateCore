package omc.boundbyfate.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.util.Identifier
import omc.boundbyfate.api.race.RaceDefinition
import omc.boundbyfate.api.race.RaceSenses
import omc.boundbyfate.api.race.RaceSize
import omc.boundbyfate.api.race.SubraceDefinition
import org.slf4j.LoggerFactory
import java.io.InputStream

/**
 * Parses race and subrace definitions from JSON.
 *
 * Race format (data/<ns>/bbf_race/<name>.json):
 * ```json
 * {
 *   "displayName": "Дварф",
 *   "size": "MEDIUM",
 *   "speedMultiplier": 0.9,
 *   "statBonuses": { "boundbyfate-core:constitution": 2 },
 *   "senses": { "darkvision": 60 },
 *   "resistances": { "boundbyfate-core:poison": -1 },
 *   "proficiencies": ["boundbyfate-core:save_constitution"],
 *   "itemProficiencies": ["boundbyfate-core:axes_weapon"],
 *   "abilities": ["boundbyfate-core:stonecunning"],
 *   "subraces": ["boundbyfate-core:hill_dwarf"]
 * }
 * ```
 *
 * Subrace format (data/<ns>/bbf_subrace/<name>.json):
 * ```json
 * {
 *   "displayName": "Холмовой дварф",
 *   "parentRace": "boundbyfate-core:dwarf",
 *   "statBonuses": { "boundbyfate-core:wisdom": 1 },
 *   "abilities": ["boundbyfate-core:dwarven_toughness"]
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

            val speedMultiplier = json.get("speedMultiplier")?.asFloat ?: 1.0f

            val statBonuses = parseIdentifierIntMap(json, "statBonuses", id)
            val resistances = parseIdentifierIntMap(json, "resistances", id)

            val sensesObj = json.getAsJsonObject("senses")
            val senses = RaceSenses(
                darkvision  = sensesObj?.get("darkvision")?.asInt  ?: 0,
                blindsight  = sensesObj?.get("blindsight")?.asInt  ?: 0,
                tremorsense = sensesObj?.get("tremorsense")?.asInt ?: 0,
                truesight   = sensesObj?.get("truesight")?.asInt   ?: 0
            )

            RaceDefinition(
                id = id,
                displayName = displayName,
                size = size,
                scaleOverride = scaleOverride,
                speedMultiplier = speedMultiplier,
                statBonuses = statBonuses,
                senses = senses,
                resistances = resistances,
                proficiencies = parseIdentifierList(json, "proficiencies", id),
                itemProficiencies = parseIdentifierList(json, "itemProficiencies", id),
                abilities = parseIdentifierList(json, "abilities", id),
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

            SubraceDefinition(
                id = id,
                displayName = displayName,
                parentRace = Identifier(parentRaceStr),
                statBonuses = parseIdentifierIntMap(json, "statBonuses", id),
                resistances = parseIdentifierIntMap(json, "resistances", id),
                proficiencies = parseIdentifierList(json, "proficiencies", id),
                itemProficiencies = parseIdentifierList(json, "itemProficiencies", id),
                abilities = parseIdentifierList(json, "abilities", id)
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
