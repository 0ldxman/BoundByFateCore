package omc.boundbyfate.system.check

import omc.boundbyfate.api.dice.RollResult

/**
 * Result of a skill check or saving throw.
 *
 * @property request The original check request
 * @property diceResult The raw dice roll result
 * @property statModifier The stat modifier applied (e.g. STR +3)
 * @property proficiencyBonus The proficiency bonus applied (0 if not proficient)
 * @property total Final total (dice + statModifier + proficiencyBonus)
 * @property success True if total >= dc (null if no DC was set)
 */
data class CheckResult(
    val request: CheckRequest,
    val diceResult: RollResult,
    val statModifier: Int,
    val proficiencyBonus: Int,
    val total: Int,
    val success: Boolean?
) {
    /** True if the raw d20 roll was a natural 20 */
    val isCriticalSuccess: Boolean get() = diceResult.isCriticalSuccess

    /** True if the raw d20 roll was a natural 1 */
    val isCriticalFailure: Boolean get() = diceResult.isCriticalFailure
}
