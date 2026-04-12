package omc.boundbyfate.api.ability

import net.minecraft.util.Identifier
import omc.boundbyfate.api.ability.component.*

/**
 * Определение способности.
 * 
 * Центральная структура данных, описывающая способность через композицию компонентов.
 * Загружается из JSON датапаков.
 * 
 * Пример JSON:
 * ```json
 * {
 *   "id": "boundbyfate-core:fireball",
 *   "displayName": "Fireball",
 *   "activation": { "type": "Charged", "preparationTime": 80 },
 *   "targeting": { "type": "Projectile", "projectileEntity": "boundbyfate-core:fireball_projectile" },
 *   "effects": [
 *     { "type": "boundbyfate-core:damage", "dice": { "count": 8, "type": "D6" } }
 *   ]
 * }
 * ```
 */
data class AbilityDefinition(
    /** Уникальный идентификатор способности */
    val id: Identifier,
    
    /** Отображаемое имя */
    val displayName: String,
    
    /** Описание способности */
    val description: String = "",
    
    /** Иконка (формат: "item:minecraft:fire_charge" или "texture:path/to/icon.png") */
    val icon: String = "item:minecraft:nether_star",
    
    // ═══ ОБЯЗАТЕЛЬНЫЕ КОМПОНЕНТЫ ═══
    
    /** Механика активации */
    val activation: ActivationComponent,
    
    /** Механика выбора целей */
    val targeting: TargetingComponent,
    
    /** Эффекты способности (минимум 1) */
    val effects: List<EffectEntry>,
    
    // ═══ ОПЦИОНАЛЬНЫЕ КОМПОНЕНТЫ ═══
    
    /** Стоимость использования */
    val costs: List<CostComponent> = emptyList(),
    
    /** Глобальные условия активации */
    val conditions: List<ConditionComponent> = emptyList(),
    
    /** Визуальные эффекты */
    val visuals: List<VisualComponent> = emptyList(),
    
    /** Масштабирование */
    val scaling: List<ScalingComponent> = emptyList(),
    
    /** Метаданные */
    val metadata: List<MetadataComponent> = emptyList()
) {
    init {
        require(displayName.isNotBlank()) { "displayName cannot be blank for ability $id" }
        require(effects.isNotEmpty()) { "Ability $id must have at least one effect" }
    }
    
    /**
     * Выполняет эффекты для указанной фазы.
     * 
     * @param context Контекст выполнения
     * @return true если все эффекты успешно выполнены
     */
    fun executePhase(context: AbilityContext): Boolean {
        var success = true
        for (effectEntry in effects) {
            if (!effectEntry.execute(context)) {
                success = false
                if (effectEntry.stopOnFailure) break
            }
        }
        return success
    }
    
    // ═══ ХЕЛПЕРЫ ДЛЯ ДОСТУПА К КОМПОНЕНТАМ ═══
    
    inline fun <reified T : CostComponent> getCost(): T? = 
        costs.filterIsInstance<T>().firstOrNull()
    
    inline fun <reified T : VisualComponent> getVisuals(): List<T> = 
        visuals.filterIsInstance<T>()
    
    inline fun <reified T : MetadataComponent> getMetadata(): T? = 
        metadata.filterIsInstance<T>().firstOrNull()
    
    inline fun <reified T : ScalingComponent> getScaling(): T? = 
        scaling.filterIsInstance<T>().firstOrNull()
}
