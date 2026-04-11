package omc.boundbyfate.api.resource

/**
 * Defines when a resource pool recovers.
 */
enum class RecoveryType {
    /** Recovers on long rest (sleep) */
    LONG_REST,

    /** Recovers on short rest */
    SHORT_REST,

    /** Recovers every combat turn */
    TURN,

    /** Only recovers manually (via command or ability) */
    MANUAL
}
