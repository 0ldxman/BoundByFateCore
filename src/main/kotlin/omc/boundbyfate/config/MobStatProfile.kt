package omc.boundbyfate.config

import com.google.gson.JsonObject
import net.minecraft.util.Identifier

/**
 * Full bestiary profile for a mob type.
 *
 * Loaded from `world/boundbyfate/mobs/{namespace}_{path}.json`.
 *
 * Example JSON:
 * ```json
 * {
 *   "mobTypeId": "minecraft:zombie",
 *   "challengeRating": 0.25,
 *   "experienceReward": 50,
 *   "armorClass": 8,
 *   "baseStats": {
 *     "boundbyfate-core:strength": 13,
 *     "boundbyfate-core:constitution": 15,
 *     "boundbyfate-core:dexterity": 8,
 *     "boundbyfate-core:intelligence": 3,
 *     "boundbyfate-core:wisdom": 6,
 *     "boundbyfate-core:charisma": 5
 *   },
 *   "senses": {
 *     "darkvision": 60
 *   },
 *   "resistances": {
 *     "boundbyfate-core:necrotic": -3,
 *     "boundbyfate-core:poison": -3
 *   },
 *   "traits": [
 *     "boundbyfate-core:undead_fortitude"
 *   ]
 * }
 * ```
 *
 * @property mobTypeId Entity type identifier
 * @property challengeRating CR value (0.125, 0.25, 0.5, 1, 2, ... 30)
 * @property experienceReward XP given to players on kill
 * @property armorClass Armor Class - difficulty to hit (used in combat system later)
 * @property baseStats D&D ability scores
 * @property senses Sensory capabilities (darkvision range, etc.)
 * @property resistances Damage type resistance levels (uses ResistanceLevel system)
 * @property traits Ability/trait IDs (stubs for future ability system)
 */
data class MobStatProfile(
    val mobTypeId: Identifier,
    val challengeRating: Float = 0f,
    val experienceReward: Int = 0,
    val armorClass: Int = 10,
    val baseStats: Map<Identifier, Int> = emptyMap(),
    val senses: MobSenses = MobSenses(),
    val resistances: Map<Identifier, Int> = emptyMap(),
    val traits: List<Identifier> = emptyList()
) {
    /**
     * Proficiency bonus derived from CR (same formula as player level).
     * CR 0-4 → +2, CR 5-8 → +3, CR 9-12 → +4, CR 13-16 → +5, CR 17+ → +6
     */
    val proficiencyBonus: Int get() = when {
        challengeRating < 5f  -> 2
        challengeRating < 9f  -> 3
        challengeRating < 13f -> 4
        challengeRating < 17f -> 5
        else                  -> 6
    }
}

/**
 * Sensory capabilities of a mob.
 *
 * @property darkvision Range in blocks (0 = none)
 * @property blindsight Range in blocks (0 = none)
 * @property tremorsense Range in blocks (0 = none)
 * @property truesight Range in blocks (0 = none)
 */
data class MobSenses(
    val darkvision: Int = 0,
    val blindsight: Int = 0,
    val tremorsense: Int = 0,
    val truesight: Int = 0
)
