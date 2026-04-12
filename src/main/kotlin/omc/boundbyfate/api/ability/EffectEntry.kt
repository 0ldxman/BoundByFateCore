package omc.boundbyfate.api.ability

import omc.boundbyfate.api.ability.component.ConditionComponent

/**
 * Обёртка для эффекта с условиями и настройками выполнения.
 * 
 * Позволяет:
 * - Привязать эффект к определённой фазе
 * - Добавить условия для выполнения
 * - Настроить поведение при неудаче
 * - Выбрать режим выполнения (для каждой цели или один раз)
 */
data class EffectEntry(
    /** Эффект для выполнения */
    val effect: AbilityEffect,
    
    /** Фаза, в которой выполняется эффект */
    val phase: AbilityPhase = AbilityPhase.APPLICATION,
    
    /** Условия, которые должны быть выполнены */
    val conditions: List<ConditionComponent> = emptyList(),
    
    /** Выполнять ли эффект для каждой цели отдельно */
    val executeOnEachTarget: Boolean = true,
    
    /** Остановить ли выполнение способности при неудаче этого эффекта */
    val stopOnFailure: Boolean = false
) {
    /**
     * Проверяет условия и применяет эффект.
     * 
     * @param context Контекст выполнения способности
     * @return true если эффект успешно выполнен или не должен останавливать выполнение
     */
    fun execute(context: AbilityContext): Boolean {
        // Проверка фазы
        if (context.phase != phase) return true
        
        // Проверка условий
        for (condition in conditions) {
            if (!condition.check(context)) {
                return !stopOnFailure
            }
        }
        
        // Проверка canApply
        if (!effect.canApply(context)) {
            return !stopOnFailure
        }
        
        // Применение эффекта
        return if (executeOnEachTarget && context.hasTargets()) {
            // Для каждой цели отдельно
            var allSuccess = true
            for (target in context.targets) {
                val targetContext = context.copy(targets = listOf(target))
                if (!effect.apply(targetContext)) {
                    allSuccess = false
                    if (stopOnFailure) break
                }
            }
            allSuccess || !stopOnFailure
        } else {
            // Один раз для всех целей
            effect.apply(context) || !stopOnFailure
        }
    }
}
