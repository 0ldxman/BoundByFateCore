package omc.boundbyfate.registry

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.Identifier
import omc.boundbyfate.component.PlayerLevelData

/**
 * Registry for all BoundByFate data attachments.
 */
object BbfAttachments {
    /**
     * Player level and experience data attachment.
     * Persists through death and world reload.
     */
    val PLAYER_LEVEL: AttachmentType<PlayerLevelData> = AttachmentRegistry.createPersistent(
        Identifier("boundbyfate-core", "player_level"),
        { tag -> PlayerLevelData.readFromNbt(tag as NbtCompound) },
        { data, tag -> data.writeToNbt(tag as NbtCompound) }
    )
    
    /**
     * Initialize all attachments. Called during mod initialization.
     */
    fun register() {
        // Attachments are registered on creation, but we call this
        // to ensure the object is initialized during mod startup
    }
}
