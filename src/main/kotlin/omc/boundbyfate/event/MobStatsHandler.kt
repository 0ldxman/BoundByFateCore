package omc.boundbyfate.event

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.MobEntity
import omc.boundbyfate.config.MobStatConfigLoader
import org.slf4j.LoggerFactory

/**
 * Handles mob stat initialization.
 * 
 * Note: Currently just creates the config directory.
 * Full mob stats implementation will be added later.
 */
object MobStatsHandler {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")
    
    /**
     * Registers event handlers.
     */
    fun register() {
        ServerEntityEvents.ENTITY_LOAD.register { entity, world ->
            if (entity is MobEntity) {
                onMobLoad(entity)
            }
        }
    }
    
    /**
     * Called when a mob loads.
     * Currently just ensures config directory exists.
     */
    private fun onMobLoad(mob: MobEntity) {
        try {
            // Get world directory (same method as PlayerStatsHandler)
            val serverWorld = mob.world as? net.minecraft.server.world.ServerWorld ?: return
            val stateManager = serverWorld.persistentStateManager
            
            val worldDir = try {
                val directoryField = stateManager.javaClass.getDeclaredField("directory")
                directoryField.isAccessible = true
                (directoryField.get(stateManager) as java.io.File).toPath().parent
            } catch (e: Exception) {
                // Fallback
                val server = serverWorld.server
                val runDir = server.runDirectory.toPath()
                if (server.isDedicated) {
                    runDir.resolve(server.saveProperties.levelName)
                } else {
                    runDir.resolve("saves").resolve(server.saveProperties.levelName)
                }
            }
            
            // Try to load mob config (this will create the directory)
            // We only do this once per world load to avoid spam
            if (!directoryCreated) {
                MobStatConfigLoader.load(worldDir, mob.type.registryEntry.registryKey().value)
                directoryCreated = true
            }
            
            // TODO: Apply mob stats when system is fully implemented
        } catch (e: Exception) {
            // Silently ignore - mob stats are optional
        }
    }
    
    private var directoryCreated = false
}
