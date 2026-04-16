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
 * Roll thresholds on death (roll + CON modifier):
 *   nat 1  → -2 (always, regardless of CON)
 *   nat 20 → +1 vitality (lucky recovery)
 *   total ≥10 → no loss
 *   total <10  → -1
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
        // loss > 0 = vitality lost, loss < 0 = vitality gained (nat 20)
        val loss = when {
            roll == 1   ->  2   // nat 1 — always -2 regardless of CON
            total >= 10 ->  0   // success — no loss
            else        ->  1   // failure (total < 10) — -1
        }
        val scarGained = if (loss > 0) 1 else 0
        return Pair(
            copy(
                vitality = (vitality - loss).coerceIn(0, MAX_VITALITY),
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
