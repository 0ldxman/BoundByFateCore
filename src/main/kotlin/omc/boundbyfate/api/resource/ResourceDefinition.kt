package omc.boundbyfate.api.resource

import net.minecraft.util.Identifier

/**
 * Immutable definition of a resource pool type.
 *
 * Registered in [omc.boundbyfate.registry.ResourceRegistry].
 * Describes what a resource is, not how much a specific entity has.
 *
 * Examples:
 * - Spell slots level 1-9
 * - Rage charges (Barbarian)
 * - Ki points (Monk)
 * - Superiority dice (Battle Master Fighter)
 * - Blood Maledict charges (Blood Hunter)
 *
 * @property id Unique identifier (e.g. "boundbyfate-core:spell_slot_1")
 * @property displayName Human-readable name (e.g. "Ячейки заклинаний 1 уровня")
 * @property recoveryType When this resource recovers
 * @property defaultMaximum Default maximum value (can be overridden per entity)
 */
data class ResourceDefinition(
    val id: Identifier,
    val displayName: String,
    val recoveryType: RecoveryType,
    val defaultMaximum: Int = 0
) {
    init {
        require(displayName.isNotBlank()) { "ResourceDefinition $id: displayName cannot be blank" }
        require(defaultMaximum >= 0) { "ResourceDefinition $id: defaultMaximum cannot be negative" }
    }
}
