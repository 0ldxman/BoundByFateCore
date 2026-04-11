package omc.boundbyfate.system.damage

import net.minecraft.entity.LivingEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.component.EntityDamageData
import omc.boundbyfate.registry.BbfAttachments

/**
 * Utility for managing entity damage resistances.
 *
 * Used by races, classes, equipment and abilities to grant
 * resistances, immunities and vulnerabilities.
 *
 * Usage:
 * ```kotlin
 * // Dwarf racial resistance to poison
 * DamageResistanceSystem.addResistance(player, BbfDamageTypes.POISON.id, 0.5f)
 *
 * // Undead immunity to necrotic
 * DamageResistanceSystem.addResistance(entity, BbfDamageTypes.NECROTIC.id, 0.0f)
 *
 * // Remove resistance (e.g. when unequipping item)
 * DamageResistanceSystem.removeResistance(player, BbfDamageTypes.FIRE.id)
 * ```
 */
object DamageResistanceSystem {

    fun getModifier(entity: LivingEntity, damageTypeId: Identifier): Float {
        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_DAMAGE, null) ?: return 1.0f
        return data.getModifier(damageTypeId)
    }

    fun isImmune(entity: LivingEntity, damageTypeId: Identifier): Boolean {
        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_DAMAGE, null) ?: return false
        return data.isImmune(damageTypeId)
    }

    /**
     * Adds or updates a resistance for an entity.
     *
     * @param entity The entity to modify
     * @param damageTypeId The damage type identifier
     * @param modifier Damage multiplier (0.0=immune, 0.5=resist, 1.0=normal, 2.0=vulnerable)
     */
    fun addResistance(entity: LivingEntity, damageTypeId: Identifier, modifier: Float) {
        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_DAMAGE, EntityDamageData())
        entity.setAttached(BbfAttachments.ENTITY_DAMAGE, data.withResistance(damageTypeId, modifier))
    }

    /**
     * Removes a resistance from an entity (back to normal 1.0).
     */
    fun removeResistance(entity: LivingEntity, damageTypeId: Identifier) {
        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_DAMAGE, null) ?: return
        entity.setAttached(BbfAttachments.ENTITY_DAMAGE, data.withoutResistance(damageTypeId))
    }
}
