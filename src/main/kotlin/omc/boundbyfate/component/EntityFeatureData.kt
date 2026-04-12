package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier

/**
 * Stores active status effects, feature cooldowns, granted features and hotbar slots.
 *
 * @property activeStatuses Map of statusId -> ActiveStatus
 * @property cooldowns Map of featureId -> remaining cooldown ticks
 * @property grantedFeatures Set of feature IDs the entity has (from race/class/feats)
 * @property hotbarSlots Array of 10 feature IDs assigned to hotbar slots (null = empty)
 */
data class EntityFeatureData(
    val activeStatuses: Map<Identifier, ActiveStatus> = emptyMap(),
    val cooldowns: Map<Identifier, Int> = emptyMap(),
    val grantedFeatures: Set<Identifier> = emptySet(),
    val hotbarSlots: List<Identifier?> = List(10) { null }
) {
    /**
     * A single active status effect instance.
     *
     * @property statusId The status effect definition ID
     * @property remainingTicks Ticks until expiry (-1 = permanent)
     * @property stacks Current stack count
     * @property sourceId Who applied this status
     */
    data class ActiveStatus(
        val statusId: Identifier,
        val remainingTicks: Int,
        val stacks: Int = 1,
        val sourceId: Identifier? = null
    ) {
        val isPermanent: Boolean get() = remainingTicks == -1

        companion object {
            val CODEC: Codec<ActiveStatus> = RecordCodecBuilder.create { instance ->
                instance.group(
                    Identifier.CODEC.fieldOf("statusId").forGetter { it.statusId },
                    Codec.INT.fieldOf("remainingTicks").forGetter { it.remainingTicks },
                    Codec.INT.optionalFieldOf("stacks", 1).forGetter { it.stacks },
                    Identifier.CODEC.optionalFieldOf("sourceId").forGetter {
                        java.util.Optional.ofNullable(it.sourceId)
                    }
                ).apply(instance) { statusId, remainingTicks, stacks, sourceId ->
                    ActiveStatus(statusId, remainingTicks, stacks, sourceId.orElse(null))
                }
            }
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    fun hasStatus(statusId: Identifier): Boolean = activeStatuses.containsKey(statusId)

    fun getStatus(statusId: Identifier): ActiveStatus? = activeStatuses[statusId]

    fun getCooldown(featureId: Identifier): Int = cooldowns[featureId] ?: 0

    fun isOnCooldown(featureId: Identifier): Boolean = (cooldowns[featureId] ?: 0) > 0

    fun hasFeature(featureId: Identifier): Boolean = grantedFeatures.contains(featureId)

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun withStatus(status: ActiveStatus): EntityFeatureData =
        copy(activeStatuses = activeStatuses + (status.statusId to status))

    fun withoutStatus(statusId: Identifier): EntityFeatureData =
        copy(activeStatuses = activeStatuses - statusId)

    fun withCooldown(featureId: Identifier, ticks: Int): EntityFeatureData =
        copy(cooldowns = cooldowns + (featureId to ticks))

    fun withFeature(featureId: Identifier): EntityFeatureData =
        copy(grantedFeatures = grantedFeatures + featureId)

    fun withoutFeature(featureId: Identifier): EntityFeatureData =
        copy(grantedFeatures = grantedFeatures - featureId)

    // ── Hotbar slots ──────────────────────────────────────────────────────────

    fun getHotbarSlot(slot: Int): Identifier? = hotbarSlots.getOrNull(slot)

    fun withHotbarSlot(slot: Int, featureId: Identifier?): EntityFeatureData {
        if (slot !in 0..9) return this
        val newSlots = hotbarSlots.toMutableList()
        newSlots[slot] = featureId
        return copy(hotbarSlots = newSlots)
    }

    /** Tick down all cooldowns and status durations by 1 */
    fun tick(): EntityFeatureData {
        val newCooldowns = cooldowns
            .mapValues { (_, v) -> v - 1 }
            .filterValues { it > 0 }

        val newStatuses = activeStatuses
            .mapValues { (_, s) ->
                if (s.isPermanent) s else s.copy(remainingTicks = s.remainingTicks - 1)
            }

        return copy(cooldowns = newCooldowns, activeStatuses = newStatuses)
    }

    /** Returns status IDs that just expired (remainingTicks == 0) */
    fun getExpiredStatuses(): List<Identifier> =
        activeStatuses.entries
            .filter { !it.value.isPermanent && it.value.remainingTicks <= 0 }
            .map { it.key }

    companion object {
        val CODEC: Codec<EntityFeatureData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.unboundedMap(Identifier.CODEC, ActiveStatus.CODEC)
                    .optionalFieldOf("activeStatuses", emptyMap())
                    .forGetter { it.activeStatuses },
                Codec.unboundedMap(Identifier.CODEC, Codec.INT)
                    .optionalFieldOf("cooldowns", emptyMap())
                    .forGetter { it.cooldowns },
                Identifier.CODEC.listOf()
                    .optionalFieldOf("grantedFeatures", emptyList())
                    .forGetter { it.grantedFeatures.toList() },
                Identifier.CODEC.listOf()
                    .optionalFieldOf("hotbarSlots", emptyList())
                    .forGetter { slots -> slots.hotbarSlots.map { it ?: Identifier("boundbyfate-core", "empty") } }
            ).apply(instance) { statuses, cooldowns, features, hotbar ->
                val slots = hotbar.map { if (it == Identifier("boundbyfate-core", "empty")) null else it }
                    .let { list ->
                        // Pad to 10 slots
                        val padded = list.toMutableList()
                        while (padded.size < 10) padded.add(null)
                        padded.take(10)
                    }
                EntityFeatureData(statuses, cooldowns, features.toSet(), slots)
            }
        }
    }
}
