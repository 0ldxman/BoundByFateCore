package omc.boundbyfate.api.proficiency

import com.google.gson.JsonObject
import net.minecraft.util.Identifier

/**
 * Configuration for a penalty effect, loaded from JSON.
 *
 * @property type The registered penalty effect type ID
 * @property params Raw JSON parameters passed to the effect factory
 */
data class PenaltyConfig(
    val type: Identifier,
    val params: JsonObject = JsonObject()
)
