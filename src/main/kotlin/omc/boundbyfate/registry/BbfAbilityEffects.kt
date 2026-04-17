package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.dice.DiceType
import omc.boundbyfate.system.effect.*
import omc.boundbyfate.system.ability.effect.TeleportEffect
import omc.boundbyfate.system.ability.effect.TeleportMode
import org.slf4j.LoggerFactory

/**
 * Registers ability-specific effects into the unified BbfEffectRegistry.
 * Note: heal, damage, particles, sound are already registered in BoundByFateCore.
 * This registers ability-only effects (teleport, set_blocks_on_fire, etc.)
 */
object BbfAbilityEffects {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    fun register() {
        val reg = BbfEffectRegistry
        val id = { path: String -> Identifier("boundbyfate-core", path) }

        reg.register(id("teleport")) { json ->
            TeleportEffect(
                mode = TeleportMode.valueOf(json.get("mode")?.asString ?: "TO_CURSOR"),
                maxDistance = json.get("maxDistance")?.asFloat ?: 30f,
                requiresSafeLanding = json.get("requiresSafeLanding")?.asBoolean ?: true
            )
        }

        logger.info("Registered ability-specific effects")
    }
}
