package omc.boundbyfate.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.util.Identifier
import omc.boundbyfate.api.feature.*
import org.slf4j.LoggerFactory
import java.io.InputStream

/**
 * Parses FeatureDefinition and BbfStatusEffectDefinition from JSON.
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

            val type = json.get("type")?.asString?.let {
                runCatching { FeatureType.valueOf(it.uppercase()) }.getOrElse { FeatureType.PASSIVE }
            } ?: FeatureType.PASSIVE

            val trigger = json.get("trigger")?.asString?.let {
                runCatching { FeatureTrigger.valueOf(it.uppercase()) }.getOrElse { FeatureTrigger.PASSIVE }
            } ?: FeatureTrigger.PASSIVE

            val targeting = parseTargeting(json.getAsJsonObject("targeting"))
            val targetFilter = json.get("targetFilter")?.asString?.let {
                runCatching { TargetFilter.valueOf(it.uppercase()) }.getOrElse { TargetFilter.SELF_ONLY }
            } ?: TargetFilter.SELF_ONLY

            val cost = json.getAsJsonObject("cost")?.let { costObj ->
                val resourceId = costObj.get("resource")?.asString?.let { Identifier(it) } ?: return@let null
                ResourceCost(resourceId, costObj.get("amount")?.asInt ?: 1)
            }

            FeatureDefinition(
                id = id,
                displayName = displayName,
                description = json.get("description")?.asString ?: "",
                type = type,
                trigger = trigger,
                targeting = targeting,
                targetFilter = targetFilter,
                range = json.get("range")?.asFloat ?: 0f,
                cost = cost,
                cooldownTicks = json.get("cooldown")?.asInt ?: 0,
                conditions = parseConditions(json, id),
                effects = parseEffects(json.getAsJsonArray("effects"), id)
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

    private fun parseTargeting(obj: JsonObject?): TargetingMode {
        if (obj == null) return TargetingMode.Self
        return when (obj.get("type")?.asString?.lowercase()) {
            "self" -> TargetingMode.Self
            "single_target" -> TargetingMode.SingleTarget
            "sphere" -> TargetingMode.Sphere(obj.get("radius")?.asFloat ?: 5f)
            "targeted_sphere" -> TargetingMode.TargetedSphere(obj.get("radius")?.asFloat ?: 5f)
            "cylinder" -> TargetingMode.Cylinder(obj.get("radius")?.asFloat ?: 3f, obj.get("height")?.asFloat ?: 3f)
            "cone" -> TargetingMode.Cone(obj.get("length")?.asFloat ?: 5f, obj.get("angle")?.asFloat ?: 60f)
            "line" -> TargetingMode.Line(obj.get("length")?.asFloat ?: 10f, obj.get("width")?.asFloat ?: 1f)
            else -> TargetingMode.Self
        }
    }

    private fun parseEffects(array: com.google.gson.JsonArray?, id: Identifier): List<FeatureEffectConfig> {
        if (array == null) return emptyList()
        return array.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val typeStr = obj.get("type")?.asString ?: return@mapNotNull null
            val params = obj.deepCopy().asJsonObject.also { it.remove("type") }
            FeatureEffectConfig(Identifier(typeStr), params)
        }
    }

    private fun parseConditions(json: JsonObject, id: Identifier): List<FeatureConditionConfig> {
        val array = json.getAsJsonArray("conditions") ?: return emptyList()
        return array.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val typeStr = obj.get("type")?.asString ?: return@mapNotNull null
            val negate = obj.get("negate")?.asBoolean ?: false
            val params = obj.deepCopy().asJsonObject.also { it.remove("type"); it.remove("negate") }
            FeatureConditionConfig(Identifier(typeStr), negate, params)
        }
    }
}
