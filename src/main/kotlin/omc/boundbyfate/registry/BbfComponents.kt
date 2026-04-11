package omc.boundbyfate.registry

import dev.onyxstudios.cca.api.v3.component.ComponentKey
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer
import net.minecraft.util.Identifier
import omc.boundbyfate.component.PlayerLevelComponent

/**
 * Registry for all BoundByFate components.
 */
object BbfComponents : EntityComponentInitializer {
    val PLAYER_LEVEL: ComponentKey<PlayerLevelComponent> = 
        ComponentRegistry.getOrCreate(Identifier("boundbyfate-core", "player_level"), PlayerLevelComponent::class.java)
    
    override fun registerEntityComponentFactories(registry: EntityComponentFactoryRegistry) {
        // Attach level component to all players
        registry.registerForPlayers(PLAYER_LEVEL) { PlayerLevelComponent() }
    }
}
