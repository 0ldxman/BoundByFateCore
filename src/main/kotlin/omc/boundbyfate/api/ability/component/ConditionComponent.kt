package omc.boundbyfate.api.ability.component

import net.minecraft.util.Identifier
import omc.boundbyfate.api.ability.AbilityContext

/**
 * Компонент условия для проверки перед выполнением эффекта.
 */
sealed class ConditionComponent {
    /** Инвертировать ли результат проверки */
    abstract val negate: Boolean
    
    /**
     * Проверяет условие.
     * 
     * @param context Контекст выполнения способности
     * @return true если условие выполнено
     */
    abstract fun check(context: AbilityContext): Boolean
    
    /**
     * Проверка наличия ресурса.
     */
    data class HasResource(
        val resourceId: Identifier,
        val amount: Int = 1,
        override val negate: Boolean = false
    ) : ConditionComponent() {
        override fun check(context: AbilityContext): Boolean {
            // TODO: implement
            return true
        }
    }
    
    /**
     * Проверка порога здоровья.
     */
    data class HealthThreshold(
        val percentage: Float,
        val comparison: Comparison = Comparison.LESS_THAN,
        override val negate: Boolean = false
    ) : ConditionComponent() {
        override fun check(context: AbilityContext): Boolean {
            val target = context.getTarget() ?: context.caster
            val healthPercent = target.health / target.maxHealth
            
            val result = when (comparison) {
                Comparison.LESS_THAN -> healthPercent < percentage
                Comparison.GREATER_THAN -> healthPercent > percentage
                Comparison.EQUAL -> healthPercent == percentage
            }
            
            return if (negate) !result else result
        }
    }
    
    /**
     * Проверка наличия статусного эффекта.
     */
    data class HasStatusEffect(
        val effectId: Identifier,
        val checkTarget: Boolean = false,
        override val negate: Boolean = false
    ) : ConditionComponent() {
        override fun check(context: AbilityContext): Boolean {
            // TODO: implement
            return true
        }
    }
}

enum class Comparison {
    LESS_THAN,
    GREATER_THAN,
    EQUAL
}
