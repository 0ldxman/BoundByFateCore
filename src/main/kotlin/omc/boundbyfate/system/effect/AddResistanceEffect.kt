package omc.boundbyfate.system.effect

import net.minecraft.util.Identifier
import omc.boundbyfate.api.effect.BbfEffect
import omc.boundbyfate.api.effect.BbfEffectContext
import omc.boundbyfate.system.damage.DamageResistanceSystem

/**
 * Adds a damage resistance to each target.
 *
 * JSON params:
 * - damageType: String (identifier)
 * - level: Int (-3 to +2, default -1 = RESIST)
 * - sourceId: String? (override source ID, defaults to sourceId from context)
 */
class AddResistanceEffect(
    private val damageTypeId: Identifier,
    private val level: Int = -1,
    private val sourceIdOverride: Identifier? = null
) : BbfEffect {

    override fun apply(context: BbfEffectContext): Boolean {
        val sourceId = sourceIdOverride ?: context.sourceId
        for (target in context.targets) {
            DamageResistanceSystem.addResistance(target, sourceId, damageTypeId, level)
        }
        return true
    }
}
