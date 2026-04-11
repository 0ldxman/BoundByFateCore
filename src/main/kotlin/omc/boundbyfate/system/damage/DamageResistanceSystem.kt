package omc.boundbyfate.system.damage

import net.minecraft.entity.LivingEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.component.EntityDamageData
import omc.boundbyfate.registry.BbfAttachments

/**
 * Utility for managing entity damage resistances.
 *
 * Resistances are tracked per source - multiple sources can grant the same
 * resistance and the most protective one wins (minimum modifier).
 *
 * Usage:
 * ```kotlin
 * // Dwarf racial resistance to poison
 * DamageResistanceSystem.addResistance(
 *     entity = player,
 *     sourceId = Identifier("boundbyfate-core", "race_dwarf"),
 *     damageTypeId = BbfDamageTypes.POISON.id,
 *     modifier = 0.5f
 * )
 *
 * // Remove all resistances from a source (e.g. unequip item)
 * DamageResistanceSystem.removeSource(player, Identifier("mymod", "fire_ring"))
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
     * Adds or updates a resistance from a specific source.
     *
     * @param entity The entity to modify
     * @param sourceId Who grants this resistance (race, item, ability ID)
     * @param damageTypeId The damage type identifier
     * @param modifier Damage multiplier (0.0=immune, 0.5=resist, 1.0=normal, 2.0=vulnerable)
     */
    fun addResistance(
        entity: LivingEntity,
        sourceId: Identifier,
        damageTypeId: Identifier,
        modifier: Float
    ) {
        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_DAMAGE, EntityDamageData())
        entity.setAttached(BbfAttachments.ENTITY_DAMAGE, data.withResistance(sourceId, damageTypeId, modifier))
    }

    /**
     * Removes a specific resistance from a source.
     */
    fun removeResistance(entity: LivingEntity, sourceId: Identifier, damageTypeId: Identifier) {
        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_DAMAGE, null) ?: return
        entity.setAttached(BbfAttachments.ENTITY_DAMAGE, data.withoutResistance(sourceId, damageTypeId))
    }

    /**
     * Removes ALL resistances granted by a source.
     * Use when unequipping an item or losing a racial ability.
     */
    fun removeSource(entity: LivingEntity, sourceId: Identifier) {
        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_DAMAGE, null) ?: return
        entity.setAttached(BbfAttachments.ENTITY_DAMAGE, data.withoutSource(sourceId))
    }
}
