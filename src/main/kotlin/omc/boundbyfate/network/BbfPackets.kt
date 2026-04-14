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

    // ── Ability System ────────────────────────────────────────────────────────

    /** Client → Server: activate an ability */
    val ACTIVATE_ABILITY = Identifier("boundbyfate-core", "activate_ability")

    /** Client → Server: release a charged/channeled ability */
    val RELEASE_ABILITY = Identifier("boundbyfate-core", "release_ability")

    /** Client → Server: cancel ability activation */
    val CANCEL_ABILITY = Identifier("boundbyfate-core", "cancel_ability")

    /** Server → Client: sync ability activation state */
    val SYNC_ABILITY_ACTIVATION = Identifier("boundbyfate-core", "sync_ability_activation")

    /** Server → Client: update concentration status */
    val UPDATE_CONCENTRATION = Identifier("boundbyfate-core", "update_concentration")

    /** Server → Client: broadcast ability cast (for visual effects) */
    val BROADCAST_ABILITY_CAST = Identifier("boundbyfate-core", "broadcast_ability_cast")

    /** Server → Client: sync player character data (stats, skills, class, race, level) */
    val SYNC_PLAYER_DATA = Identifier("boundbyfate-core", "sync_player_data")

    /** Server → Client: sync all online players data to GM */
    val SYNC_GM_PLAYERS = Identifier("boundbyfate-core", "sync_gm_players")

    /** Server → Client: tell client to open GM screen */
    val OPEN_GM_SCREEN = Identifier("boundbyfate-core", "open_gm_screen")

    /** Client → Server: GM requests player data refresh */
    val GM_REQUEST_REFRESH = Identifier("boundbyfate-core", "gm_request_refresh")
}
