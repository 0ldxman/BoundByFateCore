package omc.boundbyfate.api.feature

import com.google.gson.JsonObject
import net.minecraft.util.Identifier

/**
 * Configuration for a single effect within a feature, loaded from JSON.
 *
 * @property type The registered effect type ID
 * @property params Raw JSON parameters passed to the effect factory
 */
data class FeatureEffectConfig(
    val type: Identifier,
    val params: JsonObject = JsonObject()
)

/**
 * Configuration for a single condition within a feature, loaded from JSON.
 *
 * @property type The registered condition type ID
 * @property negate If true, the condition result is inverted
 * @property params Raw JSON parameters passed to the condition factory
 */
data class FeatureConditionConfig(
    val type: Identifier,
    val negate: Boolean = false,
    val params: JsonObject = JsonObject()
)
