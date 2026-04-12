package omc.boundbyfate.api.feature

import net.minecraft.util.Identifier

/**
 * Defines a custom status effect (not a Minecraft StatusEffect).
 *
 * BbfStatusEffects are temporary conditions applied to entities.
 * They can apply effects on application, each tick, and on expiry.
 *
 * Loaded from data/<namespace>/bbf_status/<name>.json
 *
 * @property id Unique identifier
 * @property displayName Human-readable name
 * @property durationTicks Duration in ticks (-1 = permanent until removed)
 * @property tickInterval How often onTick effects fire (in ticks)
 * @property stackable Whether multiple instances can stack
 * @property maxStacks Maximum number of stacks (if stackable)
 * @property onApply Effects applied when the status is first applied
 * @property onTick Effects applied every [tickInterval] ticks
 * @property onExpire Effects applied when the status expires naturally
 * @property onRemove Effects applied when the status is forcibly removed
 */
data class BbfStatusEffectDefinition(
    val id: Identifier,
    val displayName: String,
    val durationTicks: Int = 200,
    val tickInterval: Int = 20,
    val stackable: Boolean = false,
    val maxStacks: Int = 1,
    val onApply: List<FeatureEffectConfig> = emptyList(),
    val onTick: List<FeatureEffectConfig> = emptyList(),
    val onExpire: List<FeatureEffectConfig> = emptyList(),
    val onRemove: List<FeatureEffectConfig> = emptyList()
) {
    val isPermanent: Boolean get() = durationTicks == -1
}
