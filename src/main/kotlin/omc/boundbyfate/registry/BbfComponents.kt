package omc.boundbyfate.registry

import dev.onyxstudios.cca.api.v3.component.ComponentKey
import dev.onyxstudios.cca.api.v3.component.ComponentRegistryV3
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy
import net.minecraft.util.Identifier
import omc.boundbyfate.component.PlayerLevelComponent

/**
 * Registry for all BoundByFate components.
 */
class BbfComponents : EntityComponentInitializer {
    
    override fun registerEntityComponentFactories(registry: EntityComponentFactoryRegistry) {
        // Attach level component to all players, persist through death
        registry.registerForPlayers(PLAYER_LEVEL, { PlayerLevelComponent() }, RespawnCopyStrategy.ALWAYS_COPY)
    }
    
    companion object {
        @JvmField
        val PLAYER_LEVEL: ComponentKey<PlayerLevelComponent> = 
            ComponentRegistryV3.INSTANCE.getOrCreate(Identifier("boundbyfate-core", "player_level"), PlayerLevelComponent::class.java)
    }
}
