package omc.boundbyfate.system.damage

import net.minecraft.entity.LivingEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.api.damage.ResistanceLevel
import omc.boundbyfate.component.EntityDamageData
import omc.boundbyfate.registry.BbfAttachments

/**
 * Utility for managing entity damage resistances.
 *
 * Resistances stack additively across sources.
 * Final level is clamped to [-3, +2].
 *
 * Usage:
 * ```kotlin
 * // Dwarf racial resist to poison (-1)
 * DamageResistanceSystem.addResistance(
 *     entity = player,
 *     sourceId = Identifier("boundbyfate-core", "race_dwarf"),
 *     damageTypeId = BbfDamageTypes.POISON.id,
 *     level = ResistanceLevel.RESIST
 * )
 *
 * // Boss immunity to fire (-3)
 * DamageResistanceSystem.addResistance(
 *     entity = boss,
 *     sourceId = Identifier("boundbyfate-core", "boss_fire_lord"),
 *     damageTypeId = BbfDamageTypes.FIRE.id,
 *     level = ResistanceLevel.IMMUNITY
 * )
 *
 * // Artifact removes immunity (+3 to counter -3)
 * DamageResistanceSystem.addResistance(
 *     entity = boss,
 *     sourceId = Identifier("mymod", "flame_curse_artifact"),
 *     damageTypeId = BbfDamageTypes.FIRE.id,
 *     level = 3  // +3 cancels -3 immunity → Normal
 * )
 *
 * // Remove all resistances from a source
 * DamageResistanceSystem.removeSource(player, Identifier("mymod", "fire_ring"))
 * ```
 */
object DamageResistanceSystem {

    fun getResistanceLevel(entity: LivingEntity, damageTypeId: Identifier): ResistanceLevel {
        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_DAMAGE, null) ?: return ResistanceLevel.NORMAL
        return data.getResistanceLevel(damageTypeId)
    }

    fun getModifier(entity: LivingEntity, damageTypeId: Identifier): Float {
        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_DAMAGE, null) ?: return 1.0f
        return data.getModifier(damageTypeId)
    }

    fun isImmune(entity: LivingEntity, damageTypeId: Identifier): Boolean {
        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_DAMAGE, null) ?: return false
        return data.isImmune(damageTypeId)
    }

    /**
     * Adds a resistance contribution from a source using ResistanceLevel enum.
     */
    fun addResistance(
        entity: LivingEntity,
        sourceId: Identifier,
        damageTypeId: Identifier,
        level: ResistanceLevel
    ) {
        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_DAMAGE, EntityDamageData())
        entity.setAttached(BbfAttachments.ENTITY_DAMAGE, data.withResistance(sourceId, damageTypeId, level))
    }

    /**
     * Adds a resistance contribution from a source using a raw integer level.
     * Useful for countering existing resistances (e.g. +3 to cancel -3 immunity).
     */
    fun addResistance(
        entity: LivingEntity,
        sourceId: Identifier,
        damageTypeId: Identifier,
        level: Int
    ) {
        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_DAMAGE, EntityDamageData())
        entity.setAttached(BbfAttachments.ENTITY_DAMAGE, data.withResistance(sourceId, damageTypeId, level))
    }

    /**
     * Removes a specific resistance contribution from a source.
     */
    fun removeResistance(entity: LivingEntity, sourceId: Identifier, damageTypeId: Identifier) {
        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_DAMAGE, null) ?: return
        entity.setAttached(BbfAttachments.ENTITY_DAMAGE, data.withoutResistance(sourceId, damageTypeId))
    }

    /**
     * Removes ALL contributions from a source.
     * Use when unequipping an item or losing a racial/class ability.
     */
    fun removeSource(entity: LivingEntity, sourceId: Identifier) {
        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_DAMAGE, null) ?: return
        entity.setAttached(BbfAttachments.ENTITY_DAMAGE, data.withoutSource(sourceId))
    }
}
