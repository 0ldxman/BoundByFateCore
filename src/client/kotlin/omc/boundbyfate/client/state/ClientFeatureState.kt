package omc.boundbyfate.client.state

import net.minecraft.util.Identifier

/**
 * Client-side state for features.
 * Populated by sync packets from the server.
 */
object ClientFeatureState {
    /** Features assigned to hotbar slots 0-9 */
    val hotbarSlots: Array<Identifier?> = arrayOfNulls(10)

    /** All features granted to this player */
    val grantedFeatures: MutableSet<Identifier> = mutableSetOf()

    fun setHotbarSlot(slot: Int, featureId: Identifier?) {
        if (slot in 0..9) hotbarSlots[slot] = featureId
    }

    fun getHotbarSlot(slot: Int): Identifier? = hotbarSlots.getOrNull(slot)

    fun clear() {
        hotbarSlots.fill(null)
        grantedFeatures.clear()
    }
}
