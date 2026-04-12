package omc.boundbyfate.api.feature

/**
 * Defines when a feature activates.
 */
enum class FeatureTrigger {
    /** Always active, applied on grant */
    PASSIVE,

    /** Player manually activates via keybind or command */
    MANUAL,

    /** Triggers when the caster hits an entity */
    ON_HIT,

    /** Triggers when the caster takes damage */
    ON_TAKE_DAMAGE,

    /** Triggers when the caster kills an entity */
    ON_KILL,

    /** Triggers at the start of each combat turn */
    ON_TURN_START,

    /** Triggers when the caster casts a spell */
    ON_SPELL_CAST,

    /** Triggers when the caster is hit by a specific damage type */
    ON_TAKE_DAMAGE_TYPE
}

enum class FeatureType {
    PASSIVE,
    ACTIVE
}
