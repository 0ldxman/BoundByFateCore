package omc.boundbyfate.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.util.Identifier
import omc.boundbyfate.api.proficiency.PenaltyConfig
import omc.boundbyfate.api.proficiency.ProficiencyDefinition
import omc.boundbyfate.api.proficiency.ProficiencyEntry
import org.slf4j.LoggerFactory
import java.io.InputStream

/**
 * Parses proficiency definitions from JSON.
 *
 * Format for container proficiency (groups others):
 * ```json
 * {
 *   "displayName": "Воинское оружие",
 *   "includes": [
 *     "boundbyfate-core:swords",
 *     "boundbyfate-core:axes"
 *   ]
 * }
 * ```
 *
 * Format for leaf proficiency (direct items/blocks):
 * ```json
 * {
 *   "displayName": "Мечи",
 *   "entries": [
 *     {
 *       "displayName": "Мечи",
 *       "tags": ["minecraft:swords"],
 *       "items": ["minecraft:iron_sword"],
 *       "penalty": {
 *         "type": "boundbyfate-core:attack_chance",
 *         "missChance": 0.4
 *       }
 *     }
 *   ]
 * }
 * ```
 */
object ProficiencyParser {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    fun parse(id: Identifier, stream: InputStream): ProficiencyDefinition? {
        return try {
            val json = JsonParser.parseReader(stream.reader()) as? JsonObject ?: run {
                logger.error("Proficiency $id: root must be a JSON object")
                return null
            }

            val displayName = json.get("displayName")?.asString ?: run {
                logger.error("Proficiency $id: missing 'displayName'")
                return null
            }

            // Container type: has "includes"
            val includes = mutableListOf<Identifier>()
            json.getAsJsonArray("includes")?.forEach { el ->
                try { includes.add(Identifier(el.asString)) }
                catch (e: Exception) { logger.warn("$id: invalid include '${el.asString}'") }
            }

            // Leaf type: has "entries"
            val entries = mutableListOf<ProficiencyEntry>()
            json.getAsJsonArray("entries")?.forEach { el ->
                val entryObj = el as? JsonObject ?: return@forEach
                parseEntry(id, entryObj)?.let { entries.add(it) }
            }

            ProficiencyDefinition(
                id = id,
                displayName = displayName,
                includes = includes,
                entries = entries
            )
        } catch (e: Exception) {
            logger.error("Failed to parse proficiency $id", e)
            null
        }
    }

    private fun parseEntry(profId: Identifier, obj: JsonObject): ProficiencyEntry? {
        val displayName = obj.get("displayName")?.asString ?: run {
            logger.warn("$profId: entry missing 'displayName'")
            return null
        }

        val items = parseIdentifierList(obj, "items", profId)
        val itemTags = parseIdentifierList(obj, "tags", profId)
        val blocks = parseIdentifierList(obj, "blocks", profId)
        val blockTags = parseIdentifierList(obj, "blockTags", profId)

        val penaltyObj = obj.getAsJsonObject("penalty") ?: run {
            logger.warn("$profId entry '$displayName': missing 'penalty'")
            return null
        }

        val penaltyTypeStr = penaltyObj.get("type")?.asString ?: run {
            logger.warn("$profId entry '$displayName': penalty missing 'type'")
            return null
        }

        // Extract params (everything except "type")
        val params = JsonObject()
        penaltyObj.entrySet().filter { it.key != "type" }.forEach { (k, v) ->
            params.add(k, v)
        }

        val penalty = PenaltyConfig(
            type = Identifier(penaltyTypeStr),
            params = params
        )

        return ProficiencyEntry(
            displayName = displayName,
            items = items,
            itemTags = itemTags,
            blocks = blocks,
            blockTags = blockTags,
            penalty = penalty
        )
    }

    private fun parseIdentifierList(obj: JsonObject, key: String, profId: Identifier): List<Identifier> {
        val result = mutableListOf<Identifier>()
        obj.getAsJsonArray(key)?.forEach { el ->
            try { result.add(Identifier(el.asString)) }
            catch (e: Exception) { logger.warn("$profId: invalid $key entry '${el.asString}'") }
        }
        return result
    }
}
