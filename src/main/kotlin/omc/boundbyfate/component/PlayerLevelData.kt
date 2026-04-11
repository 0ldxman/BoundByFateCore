package omc.boundbyfate.component

import net.minecraft.nbt.NbtCompound

/**
 * Data class that stores player level and experience.
 * Uses Fabric Data Attachment API for persistence.
 */
data class PlayerLevelData(
    var level: Int = 1,
    var experience: Int = 0
) {
    /**
     * Add experience to the player and handle level ups.
     * @return true if player leveled up
     */
    fun addExperience(amount: Int): Boolean {
        experience += amount
        val requiredXp = getRequiredExperience(level)
        
        if (experience >= requiredXp) {
            experience -= requiredXp
            level++
            return true
        }
        return false
    }
    
    /**
     * Set player level directly (admin command).
     */
    fun setLevel(newLevel: Int) {
        level = newLevel.coerceIn(1, 20)
        experience = 0
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
    
    /**
     * Serialize to NBT for persistence.
     */
    fun writeToNbt(tag: NbtCompound) {
        tag.putInt("level", level)
        tag.putInt("experience", experience)
    }
    
    companion object {
        /**
         * Deserialize from NBT.
         */
        fun readFromNbt(tag: NbtCompound): PlayerLevelData {
            return PlayerLevelData(
                level = tag.getInt("level").coerceIn(1, 20),
                experience = tag.getInt("experience")
            )
        }
    }
}
