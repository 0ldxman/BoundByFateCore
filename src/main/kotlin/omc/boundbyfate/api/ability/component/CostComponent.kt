package omc.boundbyfate.api.ability.component

import net.minecraft.util.Identifier

/**
 * Компонент, определяющий стоимость использования способности.
 */
sealed class CostComponent {
    
    /**
     * Стоимость в ресурсах (spell slots, ki, rage, etc.).
     */
    data class Resource(
        val resourceId: Identifier,
        val amount: Int = 1
    ) : CostComponent()
    
    /**
     * Стоимость в spell slot с поддержкой апкаста.
     */
    data class SpellSlot(
        val level: Int,
        val canUpcast: Boolean = true
    ) : CostComponent()
    
    /**
     * Стоимость в здоровье.
     */
    data class Health(
        val amount: Int = 0,
        val percentage: Float = 0f,
        val canKill: Boolean = false
    ) : CostComponent()
    
    /**
     * Кулдаун.
     */
    data class Cooldown(
        val ticks: Int
    ) : CostComponent()
    
    /**
     * Материальные компоненты (для дорогих ритуалов).
     */
    data class MaterialComponents(
        /** Описание компонентов для UI */
        val description: String,
        
        /** Список требуемых предметов */
        val items: List<ItemRequirement> = emptyList(),
        
        /** Потребляются ли предметы при использовании */
        val consumed: Boolean = false
    ) : CostComponent()
    
    /**
     * Требование к предмету для материальных компонентов.
     */
    data class ItemRequirement(
        /** ID конкретного предмета (например, minecraft:gold_ore) */
        val itemId: Identifier? = null,
        
        /** Тег предметов (например, boundbyfate-core:pearls) */
        val itemTag: Identifier? = null,
        
        /** Количество предметов */
        val count: Int = 1
    ) {
        init {
            require(itemId != null || itemTag != null) { 
                "Either itemId or itemTag must be specified in ItemRequirement" 
            }
            require(count > 0) { "ItemRequirement count must be positive" }
        }
    }
}
