package omc.boundbyfate.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.util.Identifier
import omc.boundbyfate.api.feat.FeatDefinition
import omc.boundbyfate.api.feat.FeatGrant
import omc.boundbyfate.api.feat.FeatPrerequisites
import org.slf4j.LoggerFactory
import java.io.InputStream

/**
 * Parses feat definitions from JSON.
 *
 * Format:
 * ```json
 * {
 *   "displayName": "Живучий",
 *   "description": "+1 к Выносливости, восстанавливать HP при 0",
 *   "prerequisites": {
 *     "minStats": {
 *       "boundbyfate-core:constitution": 13
 *     },
 *     "requiredFeats": []
 *   },
 *   "grants": {
 *     "statBonuses": {
 *       "boundbyfate-core:constitution": 1
 *     },
 *     "abilities": ["boundbyfate-core:relentless_endurance"]
 *   }
 * }
 * ```
 */
object FeatParser {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    fun parse(id: Identifier, stream: InputStream): FeatDefinition? {
        return try {
            val json = JsonParser.parseReader(stream.reader()) as? JsonObject ?: run {
                logger.error("Feat $id: root must be a JSON object")
                return null
            }

            val displayName = json.get("displayName")?.asString ?: run {
                logger.error("Feat $id: missing 'displayName'")
                return null
            }

            val description = json.get("description")?.asString ?: ""

            val prerequisites = parsePrerequisites(id, json.getAsJsonObject("prerequisites"))
            val grants = parseGrants(id, json.getAsJsonObject("grants"))

            FeatDefinition(
                id = id,
                displayName = displayName,
                description = description,
                prerequisites = prerequisites,
                grants = grants
            )
        } catch (e: Exception) {
            logger.error("Failed to parse feat $id", e)
            null
        }
    }

    private fun parsePrerequisites(id: Identifier, obj: JsonObject?): FeatPrerequisites {
        if (obj == null) return FeatPrerequisites()

        val minStats = mutableMapOf<Identifier, Int>()
        obj.getAsJsonObject("minStats")?.entrySet()?.forEach { (key, value) ->
            try { minStats[Identifier(key)] = value.asInt }
            catch (e: Exception) { logger.warn("$id prerequisites: invalid stat '$key'") }
        }

        val requiredProfs = parseIdentifierList(obj, "requiredProficiencies", id)
        val requiredItemProfs = parseIdentifierList(obj, "requiredItemProficiencies", id)
        val requiredFeats = parseIdentifierList(obj, "requiredFeats", id)

        return FeatPrerequisites(
            minStats = minStats,
            requiredProficiencies = requiredProfs,
            requiredItemProficiencies = requiredItemProfs,
            requiredFeats = requiredFeats
        )
    }

    private fun parseGrants(id: Identifier, obj: JsonObject?): FeatGrant {
        if (obj == null) return FeatGrant()

        val statBonuses = mutableMapOf<Identifier, Int>()
        obj.getAsJsonObject("statBonuses")?.entrySet()?.forEach { (key, value) ->
            try { statBonuses[Identifier(key)] = value.asInt }
            catch (e: Exception) { logger.warn("$id grants: invalid stat '$key'") }
        }

        return FeatGrant(
            statBonuses = statBonuses,
            proficiencies = parseIdentifierList(obj, "proficiencies", id),
            itemProficiencies = parseIdentifierList(obj, "itemProficiencies", id),
            abilities = parseIdentifierList(obj, "abilities", id)
        )
    }

    private fun parseIdentifierList(obj: JsonObject, key: String, id: Identifier): List<Identifier> {
        val result = mutableListOf<Identifier>()
        obj.getAsJsonArray(key)?.forEach { el ->
            try { result.add(Identifier(el.asString)) }
            catch (e: Exception) { logger.warn("$id: invalid $key entry '${el.asString}'") }
        }
        return result
    }
}
