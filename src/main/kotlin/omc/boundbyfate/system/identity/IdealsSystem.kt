package omc.boundbyfate.system.identity

import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.api.identity.Alignment
import omc.boundbyfate.api.identity.IdealAlignment
import omc.boundbyfate.component.Flaw
import omc.boundbyfate.component.Ideal
import omc.boundbyfate.component.PlayerIdentityData
import omc.boundbyfate.component.PlayerIdealsData
import omc.boundbyfate.registry.BbfAttachments
import java.util.UUID

/**
 * System for managing player ideals and flaws.
 */
object IdealsSystem {

    // ── Ideals ────────────────────────────────────────────────────────────────

    fun getIdeals(player: ServerPlayerEntity): List<Ideal> =
        player.getAttachedOrCreate(BbfAttachments.PLAYER_IDENTITY).idealsData.ideals

    fun getFlaws(player: ServerPlayerEntity): List<Flaw> =
        player.getAttachedOrCreate(BbfAttachments.PLAYER_IDENTITY).idealsData.flaws

    /**
     * Adds a new ideal. Returns the generated ID.
     */
    fun addIdeal(player: ServerPlayerEntity, text: String, axis: IdealAlignment = IdealAlignment.ANY): String {
        val identity = player.getAttachedOrCreate(BbfAttachments.PLAYER_IDENTITY)
        val id = UUID.randomUUID().toString()
        val newIdeal = Ideal(id = id, text = text, alignmentAxis = axis)
        val newIdeals = identity.idealsData.ideals + newIdeal
        player.setAttached(
            BbfAttachments.PLAYER_IDENTITY,
            identity.copy(idealsData = identity.idealsData.copy(ideals = newIdeals))
        )
        return id
    }

    /**
     * Removes an ideal by ID.
     */
    fun removeIdeal(player: ServerPlayerEntity, id: String): Boolean {
        val identity = player.getAttachedOrCreate(BbfAttachments.PLAYER_IDENTITY)
        val newIdeals = identity.idealsData.ideals.filter { it.id != id }
        if (newIdeals.size == identity.idealsData.ideals.size) return false
        player.setAttached(
            BbfAttachments.PLAYER_IDENTITY,
            identity.copy(idealsData = identity.idealsData.copy(ideals = newIdeals))
        )
        return true
    }

    /**
     * Updates an ideal's text and/or axis.
     */
    fun updateIdeal(player: ServerPlayerEntity, id: String, text: String? = null, axis: IdealAlignment? = null): Boolean {
        val identity = player.getAttachedOrCreate(BbfAttachments.PLAYER_IDENTITY)
        val existing = identity.idealsData.ideals.find { it.id == id } ?: return false
        val updated = existing.copy(
            text = text ?: existing.text,
            alignmentAxis = axis ?: existing.alignmentAxis
        )
        val newIdeals = identity.idealsData.ideals.map { if (it.id == id) updated else it }
        player.setAttached(
            BbfAttachments.PLAYER_IDENTITY,
            identity.copy(idealsData = identity.idealsData.copy(ideals = newIdeals))
        )
        return true
    }

    /**
     * Returns ideals that are incompatible with the player's current alignment.
     */
    fun getIncompatibleIdeals(player: ServerPlayerEntity): List<Ideal> {
        val identity = player.getAttachedOrCreate(BbfAttachments.PLAYER_IDENTITY)
        val alignment = identity.alignment.currentAlignment
        return identity.idealsData.ideals.filter { !it.isCompatibleWith(alignment) }
    }

    /**
     * Checks if a specific ideal is compatible with the player's current alignment.
     */
    fun isIdealCompatible(player: ServerPlayerEntity, idealId: String): Boolean {
        val identity = player.getAttachedOrCreate(BbfAttachments.PLAYER_IDENTITY)
        val ideal = identity.idealsData.ideals.find { it.id == idealId } ?: return true
        return ideal.isCompatibleWith(identity.alignment.currentAlignment)
    }

    // ── Flaws ─────────────────────────────────────────────────────────────────

    /**
     * Adds a new flaw. Returns the generated ID.
     */
    fun addFlaw(player: ServerPlayerEntity, text: String): String {
        val identity = player.getAttachedOrCreate(BbfAttachments.PLAYER_IDENTITY)
        val id = UUID.randomUUID().toString()
        val newFlaw = Flaw(id = id, text = text)
        val newFlaws = identity.idealsData.flaws + newFlaw
        player.setAttached(
            BbfAttachments.PLAYER_IDENTITY,
            identity.copy(idealsData = identity.idealsData.copy(flaws = newFlaws))
        )
        return id
    }

    /**
     * Removes a flaw by ID.
     */
    fun removeFlaw(player: ServerPlayerEntity, id: String): Boolean {
        val identity = player.getAttachedOrCreate(BbfAttachments.PLAYER_IDENTITY)
        val newFlaws = identity.idealsData.flaws.filter { it.id != id }
        if (newFlaws.size == identity.idealsData.flaws.size) return false
        player.setAttached(
            BbfAttachments.PLAYER_IDENTITY,
            identity.copy(idealsData = identity.idealsData.copy(flaws = newFlaws))
        )
        return true
    }

    /**
     * Updates a flaw's text.
     */
    fun updateFlaw(player: ServerPlayerEntity, id: String, text: String): Boolean {
        val identity = player.getAttachedOrCreate(BbfAttachments.PLAYER_IDENTITY)
        val existing = identity.idealsData.flaws.find { it.id == id } ?: return false
        val updated = existing.copy(text = text)
        val newFlaws = identity.idealsData.flaws.map { if (it.id == id) updated else it }
        player.setAttached(
            BbfAttachments.PLAYER_IDENTITY,
            identity.copy(idealsData = identity.idealsData.copy(flaws = newFlaws))
        )
        return true
    }
}
