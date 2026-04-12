package omc.boundbyfate.api.ability

import net.minecraft.util.Identifier

/**
 * Базовый интерфейс для всех эффектов способностей.
 * 
 * Эффекты регистрируются в AbilityEffectRegistry и создаются из JSON.
 * Каждый эффект может быть расширен через моддинг.
 * 
 * Пример регистрации:
 * ```kotlin
 * AbilityEffectRegistry.register(Identifier("mymod", "lifesteal")) { json ->
 *     LifestealEffect(
 *         percentage = json.get("percentage")?.asFloat ?: 0.5f
 *     )
 * }
 * ```
 */
interface AbilityEffect {
    /**
     * Тип эффекта для сериализации и регистрации.
     * Должен быть уникальным идентификатором.
     */
    val type: Identifier
    
    /**
     * Применяет эффект к контексту.
     * 
     * @param context Контекст выполнения способности
     * @return true если эффект успешно применён, false при неудаче
     */
    fun apply(context: AbilityContext): Boolean
    
    /**
     * Проверяет, может ли эффект быть применён в данном контексте.
     * Вызывается перед apply().
     * 
     * @param context Контекст выполнения способности
     * @return true если эффект может быть применён
     */
    fun canApply(context: AbilityContext): Boolean = true
}
