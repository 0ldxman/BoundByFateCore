package omc.boundbyfate.system.stat.effect

import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributes
import omc.boundbyfate.api.stat.StatEffect
import omc.boundbyfate.api.stat.StatValue
import java.util.UUID

/**
 * StatEffect that modifies maximum health based on Constitution.
 *
 * Formula: Base 20 HP + (CON modifier * 2)
 * 
 * Example:
 * - CON 10 (modifier +0) → +0 HP
 * - CON 14 (modifier +2) → +4 HP
 * - CON 20 (modifier +5) → +10 HP
 */
object MaxHealthStatEffect : StatEffect {
    private val MODIFIER_ID = UUID.fromString("bbf00000-0000-0000-0000-000000000001")
    private const val HP_PER_MODIFIER = 2.0
    
    override fun apply(entity: LivingEntity, statValue: StatValue) {
        val attributeInstance = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH) ?: return
        
        // Remove old modifier
        attributeInstance.getModifier(MODIFIER_ID)?.let {
            attributeInstance.removeModifier(it)
        }
        
        // Calculate HP bonus: CON modifier * 2
        val hpBonus = statValue.dndModifier * HP_PER_MODIFIER
        
        // Add new modifier
        val modifier = net.minecraft.entity.attribute.EntityAttributeModifier(
            MODIFIER_ID,
            "BoundByFate CON health bonus",
            hpBonus,
            net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADDITION
        )
        attributeInstance.addPersistentModifier(modifier)
        
        // Heal entity if current health exceeds new max
        if (entity.health > entity.maxHealth) {
            entity.health = entity.maxHealth
        }
    }
}
