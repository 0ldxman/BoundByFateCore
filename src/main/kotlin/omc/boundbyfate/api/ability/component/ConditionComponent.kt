package omc.boundbyfate.api.ability.component

import net.minecraft.util.Identifier
import omc.boundbyfate.api.effect.BbfEffectContext

/**
 * Condition checked before executing an effect in an ability.
 */
sealed class ConditionComponent {
    abstract val negate: Boolean

    abstract fun check(context: BbfEffectContext): Boolean

    data class HasResource(
        val resourceId: Identifier,
        val amount: Int = 1,
        override val negate: Boolean = false
    ) : ConditionComponent() {
        override fun check(context: BbfEffectContext): Boolean {
            // TODO: implement via ResourceSystem
            return true
        }
    }

    data class HealthThreshold(
        val percentage: Float,
        val comparison: Comparison = Comparison.LESS_THAN,
        override val negate: Boolean = false
    ) : ConditionComponent() {
        override fun check(context: BbfEffectContext): Boolean {
            val target = context.primaryTarget ?: context.source
            val healthPercent = target.health / target.maxHealth
            val result = when (comparison) {
                Comparison.LESS_THAN -> healthPercent < percentage
                Comparison.GREATER_THAN -> healthPercent > percentage
                Comparison.EQUAL -> healthPercent == percentage
            }
            return if (negate) !result else result
        }
    }

    data class HasStatusEffect(
        val effectId: Identifier,
        val checkTarget: Boolean = false,
        override val negate: Boolean = false
    ) : ConditionComponent() {
        override fun check(context: BbfEffectContext): Boolean {
            // TODO: implement
            return true
        }
    }
}

enum class Comparison { LESS_THAN, GREATER_THAN, EQUAL }
