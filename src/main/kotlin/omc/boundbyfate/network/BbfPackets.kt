package omc.boundbyfate.network

import net.minecraft.util.Identifier

/**
 * Network packet identifiers for BoundByFate.
 */
object BbfPackets {
    /** Server → Client: spawn particles at a position */
    val SPAWN_PARTICLES = Identifier("boundbyfate-core", "spawn_particles")

    /** Client → Server: player wants to use a feature */
    val USE_FEATURE = Identifier("boundbyfate-core", "use_feature")

    /** Server → Client: sync feature keybind slots for a player */
    val SYNC_FEATURE_SLOTS = Identifier("boundbyfate-core", "sync_feature_slots")
}
