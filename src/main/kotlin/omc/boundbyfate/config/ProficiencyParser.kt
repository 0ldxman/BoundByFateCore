package omc.boundbyfate.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier
import omc.boundbyfate.api.proficiency.PenaltyConfig
import omc.boundbyfate.api.proficiency.ProficiencyDefinition
import org.slf4j.LoggerFactory
import java.io.InputStream

/**
 * Parses proficiency definitions from JSON.
 *
 * Container format (groups other proficiencies):
 * ```json
 * {
 *   "displayName": "Воинское оружие",
 *   "includes": ["boundbyfate-core:swords", "boundbyfate-core:axes_weapon"]
 * }
 * ```
 *
 * Leaf format (item proficiency via tag):
 * ```json
 * {
 *   "displayName": "Мечи",
 *   "itemTag": "boundbyfate-core:proficiency/swords",
 *   "penalty": {
 *     "type": "boundbyfate-core:attack_chance",
 *     "missChance": 0.4
 *   }
 * }
 * ```
 *
 * Leaf format (block proficiency):
 * ```json
 * {
 *   "displayName": "Кузнечные инструменты",
 *   "blocks": ["minecraft:anvil", "minecraft:smithing_table"],
 *   "blockTags": ["minecraft:some_tag"],
 *   "penalty": { "type": "boundbyfate-core:block_interaction" }
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

            // Container: has "includes"
            val includes = mutableListOf<Identifier>()
            json.getAsJsonArray("includes")?.forEach { el ->
                try { includes.add(Identifier(el.asString)) }
                catch (e: Exception) { logger.warn("$id: invalid include '${el.asString}'") }
            }

            if (includes.isNotEmpty()) {
                return ProficiencyDefinition(id = id, displayName = displayName, includes = includes)
            }

            // Leaf: parse itemTag, blocks, blockTags, penalty
            val itemTag = json.get("itemTag")?.asString?.let { tagStr ->
                try {
                    val tagId = Identifier(tagStr)
                    TagKey.of(net.minecraft.registry.RegistryKeys.ITEM, tagId)
                } catch (e: Exception) { logger.warn("$id: invalid itemTag '$tagStr'"); null }
            }

            val blocks = parseIdentifierList(json, "blocks", id)
            val blockTags = parseIdentifierList(json, "blockTags", id)

            val penalty = json.getAsJsonObject("penalty")?.let { penaltyObj ->
                val typeStr = penaltyObj.get("type")?.asString ?: run {
                    logger.warn("$id: penalty missing 'type'")
                    return null
                }
                val params = JsonObject()
                penaltyObj.entrySet().filter { it.key != "type" }.forEach { (k, v) -> params.add(k, v) }
                PenaltyConfig(type = Identifier(typeStr), params = params)
            }

            if (itemTag == null && blocks.isEmpty() && blockTags.isEmpty()) {
                logger.warn("$id: leaf proficiency has no itemTag, blocks, or blockTags — skipping")
                return null
            }

            if (penalty == null) {
                logger.warn("$id: leaf proficiency missing 'penalty' — skipping")
                return null
            }

            ProficiencyDefinition(
                id = id,
                displayName = displayName,
                itemTag = itemTag,
                blocks = blocks,
                blockTags = blockTags,
                penalty = penalty
            )
        } catch (e: Exception) {
            logger.error("Failed to parse proficiency $id", e)
            null
        }
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
