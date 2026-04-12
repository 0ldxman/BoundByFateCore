package omc.boundbyfate.registry

import com.google.gson.JsonObject
import net.minecraft.util.Identifier
import omc.boundbyfate.api.feature.FeatureEffect
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for feature effect factories.
 *
 * Register a new effect type:
 * ```kotlin
 * FeatureEffectRegistry.register(Identifier("mymod", "my_effect")) { params ->
 *     MyEffect(params.get("value")?.asInt ?: 0)
 * }
 * ```
 */
object FeatureEffectRegistry {
    private val factories = ConcurrentHashMap<Identifier, (JsonObject) -> FeatureEffect>()

    fun register(id: Identifier, factory: (JsonObject) -> FeatureEffect) {
        val existing = factories.putIfAbsent(id, factory)
        require(existing == null) { "Feature effect type $id is already registered" }
    }

    fun create(id: Identifier, params: JsonObject): FeatureEffect? =
        factories[id]?.invoke(params)

    fun contains(id: Identifier): Boolean = factories.containsKey(id)

    val size: Int get() = factories.size
}
