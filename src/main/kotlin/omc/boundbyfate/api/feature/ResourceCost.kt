package omc.boundbyfate.api.feature

import net.minecraft.util.Identifier

/**
 * Defines the resource cost to use a feature.
 *
 * @property resourceId The resource pool to consume
 * @property amount How much to consume
 */
data class ResourceCost(
    val resourceId: Identifier,
    val amount: Int = 1
)
