package omc.boundbyfate.system.stat.effect

import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributes
import omc.boundbyfate.api.stat.StatEffect
import omc.boundbyfate.api.stat.StatValue
import java.util.UUID

/**
 * StatEffect that modifies movement speed based on Dexterity.
 *
 * Formula: +1% speed per DEX modifier point
 * 
 * Example:
 * - DEX 10 (modifier +0) → +0% speed
 * - DEX 14 (modifier +2) → +2% speed
 * - DEX 20 (modifier +5) → +5% speed
 */
object MovementSpeedStatEffect : StatEffect {
    private val MODIFIER_ID = UUID.fromString("bbf00000-0000-0000-0000-000000000002")
    private const val SPEED_PER_MODIFIER = 0.01 // 1% per modifier point
    
    override fun apply(entity: LivingEntity, statValue: StatValue) {
        val attributeInstance = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED) ?: return
        
        // Remove old modifier
        attributeInstance.getModifier(MODIFIER_ID)?.let {
            attributeInstance.removeModifier(it)
        }
        
        // Calculate speed bonus: DEX modifier * 1%
        val speedBonus = statValue.dndModifier * SPEED_PER_MODIFIER
        
        // Add new modifier (multiplicative)
        val modifier = net.minecraft.entity.attribute.EntityAttributeModifier(
            MODIFIER_ID,
            "BoundByFate DEX speed bonus",
            speedBonus,
            net.minecraft.entity.attribute.EntityAttributeModifier.Operation.MULTIPLY_BASE
        )
        attributeInstance.addPersistentModifier(modifier)
    }
}
