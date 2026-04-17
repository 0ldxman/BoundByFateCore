package omc.boundbyfate.registry

import com.google.gson.JsonObject
import net.minecraft.util.Identifier
import omc.boundbyfate.api.effect.BbfEffect
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Single unified registry for all effect types.
 *
 * Replaces the old FeatureEffectRegistry and AbilityEffectRegistry.
 * Effects registered here can be used in both Features and Abilities.
 *
 * Registration:
 * ```kotlin
 * BbfEffectRegistry.register(Identifier("boundbyfate-core", "heal")) { params ->
 *     HealEffect(
 *         diceCount = params.get("diceCount")?.asInt ?: 1,
 *         diceType = DiceType.valueOf(params.get("diceType")?.asString ?: "D8")
 *     )
 * }
 * ```
 */
object BbfEffectRegistry {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")
    private val factories = ConcurrentHashMap<Identifier, (JsonObject) -> BbfEffect>()

    fun register(id: Identifier, factory: (JsonObject) -> BbfEffect) {
        if (factories.containsKey(id)) {
            logger.warn("Overwriting effect factory for $id")
        }
        factories.put(id, factory)
        logger.debug("Registered effect: $id")
    }

    fun create(id: Identifier, params: JsonObject): BbfEffect? {
        val factory = factories[id]
        if (factory == null) {
            logger.error("Unknown effect type: $id")
            return null
        }
        return try {
            factory(params)
        } catch (e: Exception) {
            logger.error("Failed to create effect $id", e)
            null
        }
    }

    fun contains(id: Identifier): Boolean = factories.containsKey(id)

    val size: Int get() = factories.size
}
