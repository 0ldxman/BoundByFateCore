package omc.boundbyfate.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.util.Identifier
import omc.boundbyfate.api.feature.*
import org.slf4j.LoggerFactory
import java.io.InputStream

/**
 * Parses FeatureDefinition and BbfStatusEffectDefinition from JSON.
 *
 * Feature JSON format:
 * ```json
 * {
 *   "displayName": "Тёмное зрение",
 *   "description": "...",
 *   "effects": [ { "type": "boundbyfate-core:add_darkvision", "range": 60 } ]
 * }
 * ```
 *
 * Triggered feature:
 * ```json
 * {
 *   "displayName": "Жестокая критика",
 *   "trigger": { "event": "on_critical_hit" },
 *   "effects": [ { "type": "boundbyfate-core:damage", "diceCount": 1, "diceType": "D6" } ]
 * }
 * ```
 *
 * Feature granting abilities:
 * ```json
 * {
 *   "displayName": "Дьявольское наследие",
 *   "grantsAbilities": [
 *     { "ability": "boundbyfate-core:hellish_rebuke", "minLevel": 3 },
 *     { "ability": "boundbyfate-core:darkness", "minLevel": 5 }
 *   ]
 * }
 * ```
 */
object FeatureParser {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    fun parseFeature(id: Identifier, stream: InputStream): FeatureDefinition? {
        return try {
            val json = JsonParser.parseReader(stream.reader()) as? JsonObject ?: return null

            val displayName = json.get("displayName")?.asString ?: run {
                logger.error("Feature $id: missing 'displayName'")
                return null
            }

            // Trigger: null = always-on, object = event-based
            // Skip if trigger is a string (legacy format like "PASSIVE", "MANUAL")
            val trigger = if (json.has("trigger") && json.get("trigger").isJsonObject) {
                val triggerObj = json.getAsJsonObject("trigger")
                val event = triggerObj.get("event")?.asString ?: run {
                    logger.warn("Feature $id: trigger missing 'event', ignoring trigger")
                    null
                }
                if (event != null) {
                    val filter = mutableMapOf<String, String>()
                    triggerObj.getAsJsonObject("filter")?.entrySet()?.forEach { (k, v) ->
                        filter[k] = v.asString
                    }
                    FeatureTrigger(event, filter)
                } else null
            } else null

            // grantsAbilities: list of { ability, minLevel }
            val grantsAbilities = mutableListOf<AbilityGrant>()
            json.getAsJsonArray("grantsAbilities")?.forEach { el ->
                val obj = el as? JsonObject ?: return@forEach
                val abilityId = obj.get("ability")?.asString?.let { Identifier(it) } ?: return@forEach
                val minLevel = obj.get("minLevel")?.asInt ?: 0
                grantsAbilities.add(AbilityGrant(abilityId, minLevel))
            }

            FeatureDefinition(
                id = id,
                displayName = displayName,
                description = json.get("description")?.asString ?: "",
                icon = json.get("icon")?.asString ?: "item:minecraft:nether_star",
                trigger = trigger,
                effects = parseEffects(json.getAsJsonArray("effects"), id),
                grantsAbilities = grantsAbilities
            )
        } catch (e: Exception) {
            logger.error("Failed to parse feature $id", e)
            null
        }
    }

    fun parseStatus(id: Identifier, stream: InputStream): BbfStatusEffectDefinition? {
        return try {
            val json = JsonParser.parseReader(stream.reader()) as? JsonObject ?: return null

            val displayName = json.get("displayName")?.asString ?: run {
                logger.error("Status $id: missing 'displayName'")
                return null
            }

            BbfStatusEffectDefinition(
                id = id,
                displayName = displayName,
                durationTicks = json.get("durationTicks")?.asInt ?: 200,
                tickInterval = json.get("tickInterval")?.asInt ?: 20,
                stackable = json.get("stackable")?.asBoolean ?: false,
                maxStacks = json.get("maxStacks")?.asInt ?: 1,
                onApply = parseEffects(json.getAsJsonArray("onApply"), id),
                onTick = parseEffects(json.getAsJsonArray("onTick"), id),
                onExpire = parseEffects(json.getAsJsonArray("onExpire"), id),
                onRemove = parseEffects(json.getAsJsonArray("onRemove"), id)
            )
        } catch (e: Exception) {
            logger.error("Failed to parse status $id", e)
            null
        }
    }

    fun parseEffects(array: com.google.gson.JsonArray?, id: Identifier): List<FeatureEffectConfig> {
        if (array == null) return emptyList()
        return array.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val typeStr = obj.get("type")?.asString ?: return@mapNotNull null
            val params = obj.deepCopy().asJsonObject.also { it.remove("type") }
            FeatureEffectConfig(Identifier(typeStr), params)
        }
    }
}
