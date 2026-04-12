package omc.boundbyfate.system.feature.effect

import net.minecraft.util.Identifier
import omc.boundbyfate.api.feature.FeatureContext
import omc.boundbyfate.api.feature.FeatureEffect
import omc.boundbyfate.system.feature.StatusEffectSystem

/**
 * Applies a BbfStatusEffect to each target.
 *
 * JSON params:
 * - statusId: String (identifier of the status effect)
 * - durationOverride: Int? (override the default duration in ticks)
 */
class ApplyStatusEffect(
    private val statusId: Identifier,
    private val durationOverride: Int? = null
) : FeatureEffect {

    override fun apply(context: FeatureContext) {
        for (target in context.targets) {
            StatusEffectSystem.applyStatus(
                entity = target,
                statusId = statusId,
                sourceId = context.featureId,
                durationOverride = durationOverride
            )
        }
    }
}
