package omc.boundbyfate.system.identity

import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.api.identity.Alignment
import omc.boundbyfate.api.identity.AlignmentCoordinates
import omc.boundbyfate.component.AlignmentData
import omc.boundbyfate.component.AlignmentShift
import omc.boundbyfate.component.PlayerIdentityData
import omc.boundbyfate.registry.BbfAttachments

/**
 * System for managing player alignment.
 */
object AlignmentSystem {

    /**
     * Gets player's current alignment data.
     */
    fun getAlignmentData(player: ServerPlayerEntity): AlignmentData {
        val identity = player.getAttachedOrCreate(BbfAttachments.PLAYER_IDENTITY)
        return identity.alignment
    }

    /**
     * Gets player's current alignment.
     */
    fun getAlignment(player: ServerPlayerEntity): Alignment {
        return getAlignmentData(player).currentAlignment
    }

    /**
     * Gets player's alignment coordinates.
     */
    fun getCoordinates(player: ServerPlayerEntity): AlignmentCoordinates {
        return getAlignmentData(player).coordinates
    }

    /**
     * Sets player's alignment to specific coordinates.
     * Records the change in history.
     */
    fun setAlignment(player: ServerPlayerEntity, lawChaos: Int, goodEvil: Int, reason: String = "GM set") {
        val identity = player.getAttachedOrCreate(BbfAttachments.PLAYER_IDENTITY)
        val oldData = identity.alignment
        val oldAlignment = oldData.currentAlignment
        
        val newCoords = AlignmentCoordinates(lawChaos, goodEvil)
        val newAlignment = newCoords.getAlignment()
        
        val shift = AlignmentShift(
            timestamp = System.currentTimeMillis(),
            reason = reason,
            lawChaosChange = lawChaos - oldData.coordinates.lawChaos,
            goodEvilChange = goodEvil - oldData.coordinates.goodEvil,
            oldAlignment = oldAlignment,
            newAlignment = newAlignment
        )
        
        val newData = AlignmentData(
            coordinates = newCoords,
            history = oldData.history + shift
        )
        
        player.setAttached(BbfAttachments.PLAYER_IDENTITY, PlayerIdentityData(alignment = newData))
    }

    /**
     * Adds values to player's alignment coordinates.
     * Records the change in history.
     */
    fun addAlignment(
        player: ServerPlayerEntity,
        lawChaosChange: Int,
        goodEvilChange: Int,
        reason: String = "Action"
    ) {
        val identity = player.getAttachedOrCreate(BbfAttachments.PLAYER_IDENTITY)
        val oldData = identity.alignment
        val oldAlignment = oldData.currentAlignment
        
        val newCoords = oldData.coordinates.add(lawChaosChange, goodEvilChange)
        val newAlignment = newCoords.getAlignment()
        
        // Only record if there was an actual change
        if (newCoords != oldData.coordinates) {
            val shift = AlignmentShift(
                timestamp = System.currentTimeMillis(),
                reason = reason,
                lawChaosChange = lawChaosChange,
                goodEvilChange = goodEvilChange,
                oldAlignment = oldAlignment,
                newAlignment = newAlignment
            )
            
            val newData = AlignmentData(
                coordinates = newCoords,
                history = oldData.history + shift
            )
            
            player.setAttached(BbfAttachments.PLAYER_IDENTITY, PlayerIdentityData(alignment = newData))
        }
    }

    /**
     * Checks if player's alignment is on a border (wavering conviction).
     */
    fun isOnBorder(player: ServerPlayerEntity): Pair<Boolean, Boolean> {
        return getCoordinates(player).isOnBorder()
    }

    /**
     * Gets alignment history.
     */
    fun getHistory(player: ServerPlayerEntity): List<AlignmentShift> {
        return getAlignmentData(player).history
    }

    /**
     * Clears alignment history (keeps current alignment).
     */
    fun clearHistory(player: ServerPlayerEntity) {
        val identity = player.getAttachedOrCreate(BbfAttachments.PLAYER_IDENTITY)
        val newData = AlignmentData(
            coordinates = identity.alignment.coordinates,
            history = emptyList()
        )
        player.setAttached(BbfAttachments.PLAYER_IDENTITY, PlayerIdentityData(alignment = newData))
    }
}
