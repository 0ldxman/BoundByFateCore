package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Stores a player's Vitality (Жизненная Сила) — a persistent scale from 0 to MAX_VITALITY.
 *
 * Vitality represents how close a character is to permanent death.
 * It only decreases on death (via a d20 + CON roll) and can only be
 * restored through deliberate in-game actions (rest, rituals, GM intervention).
 *
 * Roll thresholds on death:
 *   ≥15 → no change
 *   10–14 → -1
 *   5–9  → -2
 *   <5   → -3
 *   nat 1 → -3 (forced, no choice)
 *
 * Vitality = 0 → permanent death (or retirement, GM's call).
 *
 * Scars are separate permanent debuffs accumulated alongside vitality loss.
 */
data class PlayerVitalityData(
    val vitality: Int = MAX_VITALITY,
    val scarCount: Int = 0
) {
    val isDead: Boolean get() = vitality <= 0
    val isFullVitality: Boolean get() = vitality >= MAX_VITALITY

    /**
     * Applies a death roll result and returns updated data.
     * @param roll Raw d20 roll (1–20)
     * @param conModifier CON modifier of the player
     * @return Pair of (new data, vitality lost)
     */
    fun applyDeathRoll(roll: Int, conModifier: Int): Pair<PlayerVitalityData, Int> {
        val total = roll + conModifier
        val loss = when {
            roll == 1 -> 3          // nat 1 — always -3
            total >= 15 -> 0        // success — no loss
            total >= 10 -> 1        // partial — -1
            total >= 5  -> 2        // failure — -2
            else        -> 3        // critical failure — -3
        }
        val scarGained = if (loss > 0) 1 else 0
        return Pair(
            copy(
                vitality = (vitality - loss).coerceAtLeast(0),
                scarCount = scarCount + scarGained
            ),
            loss
        )
    }

    /** Restores vitality by the given amount (capped at MAX_VITALITY). */
    fun restore(amount: Int): PlayerVitalityData =
        copy(vitality = (vitality + amount).coerceAtMost(MAX_VITALITY))

    /** Sets vitality directly (GM override). */
    fun withVitality(value: Int): PlayerVitalityData =
        copy(vitality = value.coerceIn(0, MAX_VITALITY))

    /** Sets scar count directly (GM override). */
    fun withScars(value: Int): PlayerVitalityData =
        copy(scarCount = value.coerceAtLeast(0))

    companion object {
        const val MAX_VITALITY = 5

        val CODEC: Codec<PlayerVitalityData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("vitality").forGetter { it.vitality },
                Codec.INT.fieldOf("scar_count").forGetter { it.scarCount }
            ).apply(instance, ::PlayerVitalityData)
        }
    }
}
