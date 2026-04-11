package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.api.damage.ResistanceLevel

/**
 * Stores damage resistance levels for an entity, tracked per source.
 *
 * Each source contributes an integer level for a damage type.
 * The effective level is the SUM of all source contributions, clamped to [-3, +2].
 *
 * Level → Multiplier:
 * -3 = Immunity       (0.0x)
 * -2 = Strong Resist  (0.25x)
 * -1 = Resist         (0.5x)
 *  0 = Normal         (1.0x)
 * +1 = Vulnerable     (2.0x)
 * +2 = Very Vulnerable(4.0x)
 *
 * Example:
 * - Race grants RESIST(-1) to poison
 * - Artifact grants VULNERABLE(+1) to poison
 * - Effective: -1 + 1 = 0 → Normal
 *
 * @property sources Map of sourceId -> (damageTypeId -> level contribution)
 */
data class EntityDamageData(
    val sources: Map<Identifier, Map<Identifier, Int>> = emptyMap()
) {
    /**
     * Returns the effective ResistanceLevel for a damage type.
     * Sums all source contributions and clamps to [-3, +2].
     */
    fun getResistanceLevel(damageTypeId: Identifier): ResistanceLevel {
        var total = 0
        for ((_, contributions) in sources) {
            total += contributions[damageTypeId] ?: 0
        }
        return ResistanceLevel.fromLevel(total)
    }

    /**
     * Returns the effective damage multiplier for a damage type.
     */
    fun getModifier(damageTypeId: Identifier): Float =
        getResistanceLevel(damageTypeId).multiplier

    /**
     * Returns true if the entity is immune to this damage type.
     */
    fun isImmune(damageTypeId: Identifier): Boolean =
        getResistanceLevel(damageTypeId) == ResistanceLevel.IMMUNITY

    /**
     * Adds or updates a resistance contribution from a specific source.
     *
     * @param sourceId Who grants this (e.g. "boundbyfate-core:race_dwarf")
     * @param damageTypeId The damage type identifier
     * @param level Contribution level (-3 to +2, use ResistanceLevel.value)
     */
    fun withResistance(sourceId: Identifier, damageTypeId: Identifier, level: Int): EntityDamageData {
        val sourceMap = (sources[sourceId] ?: emptyMap()) + (damageTypeId to level)
        return copy(sources = sources + (sourceId to sourceMap))
    }

    /** Convenience overload using ResistanceLevel enum. */
    fun withResistance(sourceId: Identifier, damageTypeId: Identifier, level: ResistanceLevel): EntityDamageData =
        withResistance(sourceId, damageTypeId, level.value)

    /**
     * Removes a specific resistance contribution from a source.
     */
    fun withoutResistance(sourceId: Identifier, damageTypeId: Identifier): EntityDamageData {
        val sourceMap = sources[sourceId] ?: return this
        val updated = sourceMap - damageTypeId
        return if (updated.isEmpty()) {
            copy(sources = sources - sourceId)
        } else {
            copy(sources = sources + (sourceId to updated))
        }
    }

    /**
     * Removes ALL contributions from a source (e.g. unequipping an item).
     */
    fun withoutSource(sourceId: Identifier): EntityDamageData =
        copy(sources = sources - sourceId)

    companion object {
        val CODEC: Codec<EntityDamageData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.unboundedMap(
                    Identifier.CODEC,
                    Codec.unboundedMap(Identifier.CODEC, Codec.INT)
                )
                    .optionalFieldOf("sources", emptyMap())
                    .forGetter { it.sources }
            ).apply(instance, ::EntityDamageData)
        }
    }
}
