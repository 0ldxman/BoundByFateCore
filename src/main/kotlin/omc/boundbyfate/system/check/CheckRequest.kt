package omc.boundbyfate.system.check

import net.minecraft.util.Identifier
import omc.boundbyfate.api.dice.AdvantageType

/**
 * Describes a skill check or saving throw request.
 *
 * @property skillId The skill or saving throw to check
 * @property dc Difficulty Class - the target number to meet or beat (null = no DC, just roll)
 * @property advantage Advantage or disadvantage on the roll
 * @property visible Whether the player sees the roll result in chat
 * @property reason Optional description shown to DM in logs
 */
data class CheckRequest(
    val skillId: Identifier,
    val dc: Int? = null,
    val advantage: AdvantageType = AdvantageType.NONE,
    val visible: Boolean = true,
    val reason: String? = null
)
