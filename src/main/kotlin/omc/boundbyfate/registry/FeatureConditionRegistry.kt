package omc.boundbyfate.registry

import com.google.gson.JsonObject
import net.minecraft.util.Identifier
import omc.boundbyfate.api.feature.FeatureCondition
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for feature condition factories.
 *
 * Register a new condition type:
 * ```kotlin
 * FeatureConditionRegistry.register(Identifier("boundbyfate-core", "health_below")) { params ->
 *     val threshold = params.get("threshold")?.asFloat ?: 0.5f
 *     FeatureCondition { ctx -> ctx.caster.health / ctx.caster.maxHealth < threshold }
 * }
 * ```
 */
object FeatureConditionRegistry {
    private val factories = ConcurrentHashMap<Identifier, (JsonObject) -> FeatureCondition>()

    fun register(id: Identifier, factory: (JsonObject) -> FeatureCondition) {
        val existing = factories.putIfAbsent(id, factory)
        require(existing == null) { "Feature condition type $id is already registered" }
    }

    fun create(id: Identifier, params: JsonObject): FeatureCondition? =
        factories[id]?.invoke(params)

    fun contains(id: Identifier): Boolean = factories.containsKey(id)
}
