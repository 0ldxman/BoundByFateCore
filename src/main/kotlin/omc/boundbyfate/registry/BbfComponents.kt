package omc.boundbyfate.registry

import dev.onyxstudios.cca.api.v3.component.ComponentKey
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy
import net.minecraft.util.Identifier
import omc.boundbyfate.component.PlayerLevelComponent

/**
 * Registry for all BoundByFate components.
 */
object BbfComponents : EntityComponentInitializer {
    lateinit var PLAYER_LEVEL: ComponentKey<PlayerLevelComponent>
        private set
    
    override fun registerEntityComponentFactories(registry: EntityComponentFactoryRegistry) {
        // Create component key during registration phase
        PLAYER_LEVEL = ComponentRegistry.getOrCreate(
            Identifier("boundbyfate-core", "player_level"), 
            PlayerLevelComponent::class.java
        )
        
        // Attach level component to all players, persist through death
        registry.registerForPlayers(PLAYER_LEVEL, { PlayerLevelComponent() }, RespawnCopyStrategy.ALWAYS_COPY)
    }
}
