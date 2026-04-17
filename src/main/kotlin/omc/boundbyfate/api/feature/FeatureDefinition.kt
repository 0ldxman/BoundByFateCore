package omc.boundbyfate.api.feature

import net.minecraft.util.Identifier

/**
 * Immutable definition of a Feature (Особенность).
 *
 * Features are PASSIVE properties of a character, item, or creature.
 * They are never manually activated by the player — they either:
 *   1. Apply immediately on grant (ALWAYS)
 *   2. Fire automatically when a game event matches their trigger condition (ON_EVENT)
 *
 * Features can also grant Abilities to the entity that has them.
 *
 * Examples:
 *   - Тёмное зрение (ALWAYS — passive sense)
 *   - Адское сопротивление (ALWAYS — passive resistance)
 *   - Жестокая критика (ON_EVENT: on_critical_hit — extra damage dice)
 *   - Дьявольское наследие (ALWAYS — grants hellish_rebuke ability at level 3+)
 *
 * Loaded from data/<namespace>/bbf_feature/<name>.json
 */
data class FeatureDefinition(
    val id: Identifier,
    val displayName: String,
    val description: String = "",
    val icon: String = "item:minecraft:nether_star",

    /**
     * Trigger condition. Null means the feature applies immediately on grant (ALWAYS).
     * If set, the feature fires when the specified game event occurs.
     */
    val trigger: FeatureTrigger? = null,

    /**
     * Effects applied when this feature fires.
     * For ALWAYS features: applied once on grant.
     * For ON_EVENT features: applied each time the trigger condition is met.
     */
    val effects: List<FeatureEffectConfig> = emptyList(),

    /**
     * Abilities granted by this feature, optionally gated by minimum level.
     * Example: Дьявольское наследие grants hellish_rebuke at level 3.
     */
    val grantsAbilities: List<AbilityGrant> = emptyList()
) {
    init {
        require(displayName.isNotBlank()) { "FeatureDefinition $id: displayName cannot be blank" }
    }

    /** True if this feature fires on a game event rather than on grant */
    val isTriggered: Boolean get() = trigger != null
}

/**
 * Describes when a feature fires automatically.
 *
 * @property event Game event identifier (e.g. "on_critical_hit", "on_hit", "on_take_damage")
 * @property filter Optional key-value filters to narrow the condition
 *                  (e.g. {"damage_type": "boundbyfate-core:poison"} for poison saves only)
 */
data class FeatureTrigger(
    val event: String,
    val filter: Map<String, String> = emptyMap()
)

/**
 * An ability granted by a feature, optionally gated by minimum character level.
 *
 * @property abilityId The ability to grant
 * @property minLevel Minimum character level required (0 = always granted)
 */
data class AbilityGrant(
    val abilityId: Identifier,
    val minLevel: Int = 0
)

/**
 * Config entry for an effect inside a feature definition (loaded from JSON).
 */
data class FeatureEffectConfig(
    val type: Identifier,
    val params: com.google.gson.JsonObject = com.google.gson.JsonObject()
)
