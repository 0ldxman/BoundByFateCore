package omc.boundbyfate.system.resource

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.api.resource.RecoveryType
import omc.boundbyfate.component.EntityResourceData
import omc.boundbyfate.registry.BbfAttachments
import org.slf4j.LoggerFactory

/**
 * Utility system for managing entity resource pools.
 *
 * Usage:
 * ```kotlin
 * // Spend a spell slot
 * val success = ResourceSystem.spend(player, BbfResources.SPELL_SLOT_1.id, 1)
 *
 * // Long rest - restore all long rest resources
 * ResourceSystem.onLongRest(player)
 * ```
 */
object ResourceSystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    // ── Queries ───────────────────────────────────────────────────────────────

    fun canSpend(player: ServerPlayerEntity, id: Identifier, amount: Int): Boolean {
        val data = player.getAttachedOrElse(BbfAttachments.ENTITY_RESOURCES, null) ?: return false
        return data.canSpend(id, amount)
    }

    fun getCurrent(player: ServerPlayerEntity, id: Identifier): Int {
        val data = player.getAttachedOrElse(BbfAttachments.ENTITY_RESOURCES, null) ?: return 0
        return data.getCurrent(id)
    }

    fun getMaximum(player: ServerPlayerEntity, id: Identifier): Int {
        val data = player.getAttachedOrElse(BbfAttachments.ENTITY_RESOURCES, null) ?: return 0
        return data.getMaximum(id)
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    /**
     * Spends [amount] from a resource pool.
     * @return true if successful, false if insufficient resources
     */
    fun spend(player: ServerPlayerEntity, id: Identifier, amount: Int): Boolean {
        val data = player.getAttachedOrElse(BbfAttachments.ENTITY_RESOURCES, EntityResourceData())
        val updated = data.spend(id, amount) ?: run {
            logger.debug("Player ${player.name.string} tried to spend $amount of $id but only has ${data.getCurrent(id)}")
            return false
        }
        player.setAttached(BbfAttachments.ENTITY_RESOURCES, updated)
        return true
    }

    /**
     * Restores [amount] to a resource pool.
     */
    fun restore(player: ServerPlayerEntity, id: Identifier, amount: Int) {
        val data = player.getAttachedOrElse(BbfAttachments.ENTITY_RESOURCES, EntityResourceData())
        player.setAttached(BbfAttachments.ENTITY_RESOURCES, data.restore(id, amount))
    }

    /**
     * Restores a pool to its maximum.
     */
    fun restoreFull(player: ServerPlayerEntity, id: Identifier) {
        val data = player.getAttachedOrElse(BbfAttachments.ENTITY_RESOURCES, EntityResourceData())
        player.setAttached(BbfAttachments.ENTITY_RESOURCES, data.restoreFull(id))
    }

    /**
     * Adds a resource pool to the player with the given maximum.
     * Current value is set to maximum (full on creation).
     */
    fun addPool(player: ServerPlayerEntity, id: Identifier, maximum: Int) {
        val data = player.getAttachedOrElse(BbfAttachments.ENTITY_RESOURCES, EntityResourceData())
        player.setAttached(BbfAttachments.ENTITY_RESOURCES, data.withPool(id, maximum))
        logger.debug("Added resource pool $id (max=$maximum) to ${player.name.string}")
    }

    /**
     * Removes a resource pool from the player.
     */
    fun removePool(player: ServerPlayerEntity, id: Identifier) {
        val data = player.getAttachedOrElse(BbfAttachments.ENTITY_RESOURCES, EntityResourceData())
        player.setAttached(BbfAttachments.ENTITY_RESOURCES, data.withoutPool(id))
    }

    /**
     * Updates the maximum for a resource pool.
     */
    fun setMaximum(player: ServerPlayerEntity, id: Identifier, maximum: Int) {
        val data = player.getAttachedOrElse(BbfAttachments.ENTITY_RESOURCES, EntityResourceData())
        player.setAttached(BbfAttachments.ENTITY_RESOURCES, data.withMaximum(id, maximum))
    }

    // ── Rest ──────────────────────────────────────────────────────────────────

    /**
     * Restores all LONG_REST resources. Call on player sleep.
     */
    fun onLongRest(player: ServerPlayerEntity) {
        val data = player.getAttachedOrElse(BbfAttachments.ENTITY_RESOURCES, EntityResourceData())
        // Long rest restores both long rest AND short rest resources
        val updated = data
            .restoreByRecovery(RecoveryType.LONG_REST)
            .restoreByRecovery(RecoveryType.SHORT_REST)
        player.setAttached(BbfAttachments.ENTITY_RESOURCES, updated)
        logger.info("Long rest: restored resources for ${player.name.string}")
    }

    /**
     * Restores all SHORT_REST resources. Call on short rest action.
     */
    fun onShortRest(player: ServerPlayerEntity) {
        val data = player.getAttachedOrElse(BbfAttachments.ENTITY_RESOURCES, EntityResourceData())
        val updated = data.restoreByRecovery(RecoveryType.SHORT_REST)
        player.setAttached(BbfAttachments.ENTITY_RESOURCES, updated)
        logger.info("Short rest: restored resources for ${player.name.string}")
    }
}
