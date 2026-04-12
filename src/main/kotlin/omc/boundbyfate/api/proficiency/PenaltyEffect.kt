package omc.boundbyfate.api.proficiency

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

/**
 * Context passed to a penalty effect when it is applied.
 *
 * @property player The player being penalized
 * @property proficiencyId The proficiency that is missing
 * @property entryDisplayName The display name of the specific entry (e.g. "Мечи")
 */
data class PenaltyContext(
    val player: ServerPlayerEntity,
    val proficiencyId: Identifier,
    val entryDisplayName: String
)

/**
 * A penalty applied when a player uses an item/block without the required proficiency.
 *
 * Implement this interface to create custom penalties.
 * Register via [omc.boundbyfate.registry.PenaltyEffectRegistry].
 *
 * Built-in types:
 * - "boundbyfate-core:attack_damage" - reduces attack damage
 * - "boundbyfate-core:attack_chance" - adds miss chance
 * - "boundbyfate-core:block_interaction" - blocks interaction entirely
 */
fun interface PenaltyEffect {
    fun apply(context: PenaltyContext)
}
