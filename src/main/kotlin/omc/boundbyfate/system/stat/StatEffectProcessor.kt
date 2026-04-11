package omc.boundbyfate.system.stat

import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributeModifier
import omc.boundbyfate.component.EntityStatData
import omc.boundbyfate.registry.StatRegistry
import org.slf4j.LoggerFactory

/**
 * Processes and applies stat effects to entities.
 *
 * Responsible for:
 * - Applying all effects when stats change
 * - Removing old BoundByFate modifiers before applying new ones
 * - Ensuring idempotent effect application
 */
object StatEffectProcessor {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")
    
    /**
     * Applies all stat effects to an entity.
     *
     * Removes all existing BoundByFate modifiers first to ensure clean state,
     * then applies effects for all registered stats.
     *
     * @param entity The entity to apply effects to
     * @param statsData The entity's stat data
     */
    fun applyAll(entity: LivingEntity, statsData: EntityStatData) {
        // Remove all old BoundByFate modifiers first
        removeAllBbfModifiers(entity)
        
        // Apply effects for each stat
        for (statDef in StatRegistry.getAll()) {
            try {
                val statValue = statsData.getStatValue(statDef.id)
                
                for (binding in statDef.effects) {
                    binding.effect.apply(entity, statValue)
                }
            } catch (e: Exception) {
                logger.error("Failed to apply effects for stat ${statDef.id} on entity ${entity.name.string}", e)
            }
        }
    }
    
    /**
     * Reapplies effects for a specific stat.
     *
     * More efficient than applyAll when only one stat changed.
     *
     * @param entity The entity to apply effects to
     * @param statId The stat that changed
     * @param statsData The entity's stat data
     */
    fun reapply(entity: LivingEntity, statId: net.minecraft.util.Identifier, statsData: EntityStatData) {
        val statDef = StatRegistry.get(statId) ?: return
        val statValue = statsData.getStatValue(statId)
        
        try {
            for (binding in statDef.effects) {
                binding.effect.apply(entity, statValue)
            }
        } catch (e: Exception) {
            logger.error("Failed to reapply effects for stat $statId on entity ${entity.name.string}", e)
        }
    }
    
    /**
     * Removes all BoundByFate attribute modifiers from an entity.
     *
     * Identifies BoundByFate modifiers by UUID prefix (bbf00000-*) or name prefix.
     */
    private fun removeAllBbfModifiers(entity: LivingEntity) {
        for (attributeInstance in entity.attributes.attributesToSend) {
            // Collect modifiers to remove (can't modify during iteration)
            val toRemove = attributeInstance.modifiers.filter { modifier ->
                isBbfModifier(modifier)
            }
            
            // Remove them
            for (modifier in toRemove) {
                attributeInstance.removeModifier(modifier.id)
            }
        }
    }
    
    /**
     * Checks if a modifier belongs to BoundByFate.
     *
     * Identifies by:
     * - UUID starting with bbf00000-
     * - Name containing "BoundByFate"
     */
    private fun isBbfModifier(modifier: EntityAttributeModifier): Boolean {
        val uuidStr = modifier.id.toString()
        return uuidStr.startsWith("bbf00000-") || 
               modifier.name.contains("BoundByFate", ignoreCase = true)
    }
}
