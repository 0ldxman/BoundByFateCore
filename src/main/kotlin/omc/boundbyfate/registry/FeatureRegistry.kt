package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.feature.BbfStatusEffectDefinition
import omc.boundbyfate.api.feature.FeatureDefinition
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry for feature and status effect definitions.
 * Populated by datapacks on server start/reload.
 */
object FeatureRegistry {
    private val features = ConcurrentHashMap<Identifier, FeatureDefinition>()
    private val statusEffects = ConcurrentHashMap<Identifier, BbfStatusEffectDefinition>()

    // ── Features ──────────────────────────────────────────────────────────────

    fun registerFeature(definition: FeatureDefinition): FeatureDefinition {
        val existing = features.putIfAbsent(definition.id, definition)
        require(existing == null) { "Feature ${definition.id} is already registered" }
        return definition
    }

    fun getFeature(id: Identifier): FeatureDefinition? = features[id]

    fun getFeatureOrThrow(id: Identifier): FeatureDefinition =
        getFeature(id) ?: throw IllegalArgumentException("Unknown feature ID: $id")

    fun getAllFeatures(): Collection<FeatureDefinition> = features.values.toList()

    // ── Status Effects ────────────────────────────────────────────────────────

    fun registerStatus(definition: BbfStatusEffectDefinition): BbfStatusEffectDefinition {
        val existing = statusEffects.putIfAbsent(definition.id, definition)
        require(existing == null) { "Status effect ${definition.id} is already registered" }
        return definition
    }

    fun getStatus(id: Identifier): BbfStatusEffectDefinition? = statusEffects[id]

    fun getAllStatuses(): Collection<BbfStatusEffectDefinition> = statusEffects.values.toList()

    fun clearAll() {
        features.clear()
        statusEffects.clear()
    }

    val featureCount: Int get() = features.size
    val statusCount: Int get() = statusEffects.size
}
