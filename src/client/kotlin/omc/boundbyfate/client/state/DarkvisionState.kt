package omc.boundbyfate.client.state

/**
 * Client-side darkvision state.
 * Synced from server when the player has darkvision.
 */
object DarkvisionState {
    /** Darkvision range in ft (0 = no darkvision) */
    var rangeFt: Int = 0

    /** Is player currently underwater? */
    var isUnderwater: Boolean = false

    /** Range in blocks */
    val rangeBlocks: Int get() = (rangeFt / 5.0 * 1.5).toInt()

    val hasDarkvision: Boolean get() = rangeFt > 0

    fun clear() { 
        rangeFt = 0
        isUnderwater = false
    }
}
