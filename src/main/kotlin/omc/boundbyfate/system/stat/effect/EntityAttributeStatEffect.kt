package omc.boundbyfate.system.stat.effect

import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttribute
import net.minecraft.entity.attribute.EntityAttributeModifier
import omc.boundbyfate.api.stat.StatEffect
import omc.boundbyfate.api.stat.StatValue
import java.util.UUID

/**
 * StatEffect that modifies an EntityAttribute based on a stat value.
 *
 * Uses a formula to convert StatValue to attribute modifier value.
 * Automatically removes old modifiers before applying new ones.
 *
 * @property attribute The EntityAttribute to modify
 * @property modifierId Unique UUID for this modifier (must be consistent across reloads)
 * @property operation The operation type (ADD_VALUE, ADD_MULTIPLIED_BASE, ADD_MULTIPLIED_TOTAL)
 * @property formula Function to convert StatValue to modifier value
 */
class EntityAttributeStatEffect(
    private val attribute: EntityAttribute,
    private val modifierId: UUID,
    private val operation: EntityAttributeModifier.Operation,
    private val formula: (StatValue) -> Double
) : StatEffect {
    
    override fun apply(entity: LivingEntity, statValue: StatValue) {
        val attributeInstance = entity.getAttributeInstance(attribute) ?: return
        
        // Remove old modifier if exists
        attributeInstance.getModifier(modifierId)?.let {
            attributeInstance.removeModifier(it)
        }
        
        // Calculate new value
        val value = formula(statValue)
        
        // Add new modifier
        val modifier = EntityAttributeModifier(
            modifierId,
            "BoundByFate stat modifier",
            value,
            operation
        )
        attributeInstance.addPersistentModifier(modifier)
    }
    
    companion object {
        /**
         * Creates an effect that adds the D&D modifier to an attribute.
         *
         * Example: STR modifier affects attack damage
         */
        fun fromDndModifier(
            attribute: EntityAttribute,
            modifierId: UUID,
            multiplier: Double = 1.0
        ): EntityAttributeStatEffect {
            return EntityAttributeStatEffect(
                attribute = attribute,
                modifierId = modifierId,
                operation = EntityAttributeModifier.Operation.ADD_VALUE,
                formula = { it.dndModifier * multiplier }
            )
        }
        
        /**
         * Creates an effect that uses the total stat value.
         *
         * Example: DEX total affects armor calculation
         */
        fun fromTotalValue(
            attribute: EntityAttribute,
            modifierId: UUID,
            operation: EntityAttributeModifier.Operation = EntityAttributeModifier.Operation.ADD_VALUE,
            formula: (Int) -> Double
        ): EntityAttributeStatEffect {
            return EntityAttributeStatEffect(
                attribute = attribute,
                modifierId = modifierId,
                operation = operation,
                formula = { formula(it.total) }
            )
        }
    }
}
