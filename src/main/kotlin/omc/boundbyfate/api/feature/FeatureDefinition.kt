package omc.boundbyfate.api.feature

import net.minecraft.util.Identifier

/**
 * Immutable definition of a feature (особенность).
 *
 * Loaded from data/<namespace>/bbf_feature/<name>.json
 *
 * Features are the central mechanic for all active and passive abilities
 * in BoundByFate. They are used by races, classes, subclasses, feats,
 * equipment, spells, and mob traits.
 *
 * @property id Unique identifier
 * @property displayName Human-readable name
 * @property description What the feature does
 * @property type PASSIVE or ACTIVE
 * @property trigger When the feature activates
 * @property targeting How targets are selected
 * @property targetFilter Which entities are valid targets
 * @property range Range in blocks (0 = melee/self)
 * @property cost Optional resource cost
 * @property cooldownTicks Cooldown in ticks (0 = no cooldown)
 * @property conditions Conditions that must be met to activate
 * @property effects Effects applied to targets
 */
data class FeatureDefinition(
    val id: Identifier,
    val displayName: String,
    val description: String = "",
    val type: FeatureType = FeatureType.PASSIVE,
    val trigger: FeatureTrigger = FeatureTrigger.PASSIVE,
    val targeting: TargetingMode = TargetingMode.Self,
    val targetFilter: TargetFilter = TargetFilter.SELF_ONLY,
    val range: Float = 0f,
    val cost: ResourceCost? = null,
    val cooldownTicks: Int = 0,
    val conditions: List<FeatureConditionConfig> = emptyList(),
    val effects: List<FeatureEffectConfig> = emptyList()
) {
    init {
        require(displayName.isNotBlank()) { "FeatureDefinition $id: displayName cannot be blank" }
    }
}
