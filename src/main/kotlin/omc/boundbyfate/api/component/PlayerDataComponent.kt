package omc.boundbyfate.api.component

import dev.onyxstudios.cca.api.v3.component.Component
import net.minecraft.nbt.NbtCompound

/**
 * Base interface for all player data components in BoundByFate.
 * Extend this interface to create custom data attachments to players.
 */
interface PlayerDataComponent : Component {
    /**
     * Serialize component data to NBT for persistence.
     */
    override fun writeToNbt(tag: NbtCompound)
    
    /**
     * Deserialize component data from NBT.
     */
    override fun readFromNbt(tag: NbtCompound)
}
