package omc.boundbyfate.component

/**
 * Data class that stores player level and experience.
 * Uses Fabric Data Attachment API for persistence.
 */
data class PlayerLevelData(
    val level: Int = 1,
    val experience: Int = 0
) {
    /**
     * Add experience to the player and handle level ups.
     * @return new PlayerLevelData with updated values, or null if no level up
     */
    fun addExperience(amount: Int): PlayerLevelData {
        val newXp = experience + amount
        val requiredXp = getRequiredExperience(level)
        
        return if (newXp >= requiredXp) {
            // Level up
            PlayerLevelData(level + 1, newXp - requiredXp)
        } else {
            // Just add XP
            PlayerLevelData(level, newXp)
        }
    }
    
    /**
     * Check if adding XP would cause level up.
     */
    fun wouldLevelUp(amount: Int): Boolean {
        return (experience + amount) >= getRequiredExperience(level)
    }
    
    /**
     * Set player level directly (admin command).
     */
    fun withLevel(newLevel: Int): PlayerLevelData {
        return PlayerLevelData(newLevel.coerceIn(1, 20), 0)
    }
    
    /**
     * Get experience required for next level based on D&D 5e table.
     */
    fun getRequiredExperience(currentLevel: Int): Int {
        return when (currentLevel) {
            1 -> 300
            2 -> 900
            3 -> 2700
            4 -> 6500
            5 -> 14000
            6 -> 23000
            7 -> 34000
            8 -> 48000
            9 -> 64000
            10 -> 85000
            11 -> 100000
            12 -> 120000
            13 -> 140000
            14 -> 165000
            15 -> 195000
            16 -> 225000
            17 -> 265000
            18 -> 305000
            19 -> 355000
            else -> Int.MAX_VALUE // Level 20 is max
        }
    }
    
    /**
     * Get proficiency bonus based on current level.
     */
    fun getProficiencyBonus(): Int {
        return when (level) {
            in 1..4 -> 2
            in 5..8 -> 3
            in 9..12 -> 4
            in 13..16 -> 5
            in 17..20 -> 6
            else -> 2
        }
    }
}
