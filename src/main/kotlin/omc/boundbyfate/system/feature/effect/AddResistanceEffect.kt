package omc.boundbyfate.system.feature.effect

import net.minecraft.util.Identifier
import omc.boundbyfate.api.feature.FeatureContext
import omc.boundbyfate.api.feature.FeatureEffect
import omc.boundbyfate.system.damage.DamageResistanceSystem

/**
 * Adds a damage resistance to each target.
 *
 * JSON params:
 * - damageType: String (identifier)
 * - level: Int (-3 to +2, default -1 = RESIST)
 * - sourceId: String? (override source ID, default = feature ID)
 */
class AddResistanceEffect(
    private val damageTypeId: Identifier,
    private val level: Int = -1,
    private val sourceIdOverride: Identifier? = null
) : FeatureEffect {

    override fun apply(context: FeatureContext) {
        val sourceId = sourceIdOverride ?: context.featureId
        for (target in context.targets) {
            if (target is net.minecraft.entity.LivingEntity) {
                DamageResistanceSystem.addResistance(target, sourceId, damageTypeId, level)
            }
        }
    }
}
