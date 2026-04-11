package omc.boundbyfate.config

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier

/**
 * Character configuration loaded from JSON file.
 *
 * Represents a player's character setup including race, class, level, and base stats.
 * Loaded from `world/boundbyfate/characters/{playerName}.json`.
 *
 * Example JSON:
 * ```json
 * {
 *   "playerName": "Steve",
 *   "race": "boundbyfate-core:human",
 *   "class": "boundbyfate-core:fighter",
 *   "startingLevel": 1,
 *   "baseStats": {
 *     "boundbyfate-core:strength": 15,
 *     "boundbyfate-core:constitution": 14,
 *     "boundbyfate-core:dexterity": 13,
 *     "boundbyfate-core:intelligence": 10,
 *     "boundbyfate-core:wisdom": 12,
 *     "boundbyfate-core:charisma": 8
 *   }
 * }
 * ```
 *
 * @property playerName Minecraft username (must match exactly)
 * @property race Race identifier (for future Origins integration)
 * @property characterClass Class identifier (for future class system)
 * @property startingLevel Starting level (1-20)
 * @property baseStats Map of stat ID to base value
 */
data class CharacterStatProfile(
    val playerName: String,
    val race: Identifier,
    val characterClass: Identifier,
    val startingLevel: Int,
    val baseStats: Map<Identifier, Int>
) {
    companion object {
        /**
         * Codec for JSON serialization/deserialization.
         */
        val CODEC: Codec<CharacterStatProfile> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("playerName").forGetter { it.playerName },
                Identifier.CODEC.fieldOf("race").forGetter { it.race },
                Identifier.CODEC.fieldOf("class").forGetter { it.characterClass },
                Codec.INT.fieldOf("startingLevel").forGetter { it.startingLevel },
                Codec.unboundedMap(Identifier.CODEC, Codec.INT)
                    .fieldOf("baseStats")
                    .forGetter { it.baseStats }
            ).apply(instance, ::CharacterStatProfile)
        }
    }
    
    /**
     * Validates the profile.
     *
     * @return List of validation errors (empty if valid)
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        
        if (playerName.isBlank()) {
            errors.add("playerName cannot be blank")
        }
        
        if (startingLevel !in 1..20) {
            errors.add("startingLevel must be between 1 and 20, got $startingLevel")
        }
        
        if (baseStats.isEmpty()) {
            errors.add("baseStats cannot be empty")
        }
        
        // Stat value validation is done in EntityStatData.fromBaseStats()
        
        return errors
    }
}
