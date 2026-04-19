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

    /** Client → Server: request GM data refresh (for auto-sync after changes) */
    val REQUEST_GM_DATA = Identifier("boundbyfate-core", "request_gm_data")

    /** Client → Server: GM edits a player's stats */
    val GM_EDIT_PLAYER_STATS = Identifier("boundbyfate-core", "gm_edit_player_stats")

    /** Client → Server: GM edits a player's class/race/level/gender */
    val GM_EDIT_PLAYER_IDENTITY = Identifier("boundbyfate-core", "gm_edit_player_identity")

    /** Client → Server: GM edits a player's skill/save proficiencies */
    val GM_EDIT_PLAYER_SKILLS = Identifier("boundbyfate-core", "gm_edit_player_skills")

    /** Client → Server: GM adds/removes a feature from a player */
    val GM_EDIT_PLAYER_FEATURE = Identifier("boundbyfate-core", "gm_edit_player_feature")

    /** Client → Server: GM sets vitality/scars for a player */
    val GM_EDIT_PLAYER_VITALITY = Identifier("boundbyfate-core", "gm_edit_player_vitality")

    /** Client → Server: GM sets HP (current, max, temp) for a player */
    val GM_EDIT_PLAYER_HP = Identifier("boundbyfate-core", "gm_edit_player_hp")

    /** Client → Server: GM sets speed (ft) and scale for a player */
    val GM_EDIT_PLAYER_SPEED_SCALE = Identifier("boundbyfate-core", "gm_edit_player_speed_scale")

    /** Client → Server: GM sets skin for a player (by skin name) */
    val GM_SET_PLAYER_SKIN = Identifier("boundbyfate-core", "gm_set_player_skin")

    /** Server → Client: sync darkvision range to client */
    val SYNC_DARKVISION = Identifier("boundbyfate-core", "sync_darkvision")

    /** Server → Client: sync available skin names for GM picker */
    val SYNC_SKIN_LIST = Identifier("boundbyfate-core", "sync_skin_list")

    /** Server → Client: sync available classes/races/skills for GM dropdowns */
    val SYNC_GM_REGISTRY = Identifier("boundbyfate-core", "sync_gm_registry")

    /** Server → Client: sync player identity data (alignment, ideals, flaws) to GM */
    val SYNC_PLAYER_IDENTITY = Identifier("boundbyfate-core", "sync_player_identity")

    /** Client → Server: GM edits player alignment */
    val GM_EDIT_PLAYER_ALIGNMENT = Identifier("boundbyfate-core", "gm_edit_player_alignment")

    /** Client → Server: GM adds/removes/updates an ideal for a player */
    val GM_EDIT_PLAYER_IDEAL = Identifier("boundbyfate-core", "gm_edit_player_ideal")

    /** Client → Server: GM adds/removes/updates a flaw for a player */
    val GM_EDIT_PLAYER_FLAW = Identifier("boundbyfate-core", "gm_edit_player_flaw")

    /** Client → Server: GM adds/removes/updates a motivation for a player */
    val GM_EDIT_PLAYER_MOTIVATION = Identifier("boundbyfate-core", "gm_edit_player_motivation")

    /** Client → Server: GM accepts/rejects a motivation proposal */
    val GM_HANDLE_PROPOSAL = Identifier("boundbyfate-core", "gm_handle_proposal")

    /** Client → Server: GM adds/removes/updates a goal for a player */
    val GM_EDIT_PLAYER_GOAL = Identifier("boundbyfate-core", "gm_edit_player_goal")

    /** Client → Server: player proposes a motivation to GM */
    val PLAYER_PROPOSE_MOTIVATION = Identifier("boundbyfate-core", "player_propose_motivation")
    
    /** Client → Server: GM sets complete identity data for a player (replaces all delta packets) */
    val GM_SET_PLAYER_IDENTITY = Identifier("boundbyfate-core", "gm_set_player_identity")
}
