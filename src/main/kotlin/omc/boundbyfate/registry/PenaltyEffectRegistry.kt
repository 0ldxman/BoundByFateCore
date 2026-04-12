package omc.boundbyfate.registry

import com.google.gson.JsonObject
import net.minecraft.util.Identifier
import omc.boundbyfate.api.proficiency.PenaltyEffect
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for penalty effect factories.
 *
 * A factory takes JSON params and returns a [PenaltyEffect] instance.
 * Register custom penalty types here to use them in proficiency JSON configs.
 *
 * Usage:
 * ```kotlin
 * PenaltyEffectRegistry.register(
 *     Identifier("mymod", "crossbow_sway"),
 *     factory = { params -> CrossbowSwayPenalty(params.get("intensity").asFloat) }
 * )
 * ```
 */
object PenaltyEffectRegistry {
    private val factories = ConcurrentHashMap<Identifier, (JsonObject) -> PenaltyEffect>()

    fun register(id: Identifier, factory: (JsonObject) -> PenaltyEffect) {
        val existing = factories.putIfAbsent(id, factory)
        require(existing == null) { "Penalty effect type $id is already registered" }
    }

    fun create(id: Identifier, params: JsonObject): PenaltyEffect? {
        return factories[id]?.invoke(params)
    }

    fun contains(id: Identifier): Boolean = factories.containsKey(id)
}
