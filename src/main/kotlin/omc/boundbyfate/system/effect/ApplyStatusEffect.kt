package omc.boundbyfate.system.effect

import net.minecraft.util.Identifier
import omc.boundbyfate.api.effect.BbfEffect
import omc.boundbyfate.api.effect.BbfEffectContext
import omc.boundbyfate.system.feature.StatusEffectSystem

/**
 * Applies a BbfStatusEffect to each target.
 *
 * JSON params:
 * - statusId: String (identifier of the status effect definition)
 * - durationOverride: Int? (override the default duration in ticks, -1 = permanent)
 */
class ApplyStatusEffect(
    private val statusId: Identifier,
    private val durationOverride: Int? = null
) : BbfEffect {

    override fun apply(context: BbfEffectContext): Boolean {
        for (target in context.targets) {
            StatusEffectSystem.applyStatus(
                entity = target,
                statusId = statusId,
                sourceId = context.sourceId,
                durationOverride = durationOverride
            )
        }
        return true
    }
}
