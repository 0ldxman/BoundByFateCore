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

    /** Server → Client: sync feature hotbar slots for a player */
    val SYNC_FEATURE_SLOTS = Identifier("boundbyfate-core", "sync_feature_slots")

    /** Client → Server: player updated a hotbar slot */
    val UPDATE_FEATURE_SLOT = Identifier("boundbyfate-core", "update_feature_slot")

    /** Server → Client: sync all granted features to client */
    val SYNC_GRANTED_FEATURES = Identifier("boundbyfate-core", "sync_granted_features")

    /** Server → Client: sync weapon definitions to client for tooltips */
    val SYNC_WEAPON_REGISTRY = Identifier("boundbyfate-core", "sync_weapon_registry")

    /** Server → Client: show floating attack roll text above target (only to attacker) */
    val SHOW_ATTACK_ROLL = Identifier("boundbyfate-core", "show_attack_roll")

    /** Server → Client: set custom skin for a player */
    val SYNC_PLAYER_SKIN = Identifier("boundbyfate-core", "sync_player_skin")

    /** Server → Client: clear custom skin for a player (revert to Mojang skin) */
    val CLEAR_PLAYER_SKIN = Identifier("boundbyfate-core", "clear_player_skin")
}
