package omc.boundbyfate.api.component

import dev.onyxstudios.cca.api.v3.component.tick.ServerTickingComponent
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound

/**
 * Base interface for all player data components in BoundByFate.
 * Extend this interface to create custom data attachments to players.
 */
interface PlayerDataComponent : ServerTickingComponent {
    /**
     * Serialize component data to NBT for persistence.
     */
    fun writeToNbt(tag: NbtCompound)
    
    /**
     * Deserialize component data from NBT.
     */
    fun readFromNbt(tag: NbtCompound)
    
    /**
     * Called every server tick. Override if needed.
     */
    override fun serverTick() {
        // Default: do nothing
    }
}
