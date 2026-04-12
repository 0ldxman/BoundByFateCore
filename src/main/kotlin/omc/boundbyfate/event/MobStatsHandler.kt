package omc.boundbyfate.event

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.minecraft.entity.mob.MobEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import omc.boundbyfate.component.EntityStatData
import omc.boundbyfate.config.MobStatConfigLoader
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.StatRegistry
import omc.boundbyfate.system.damage.DamageResistanceSystem
import omc.boundbyfate.system.stat.StatEffectProcessor
import org.slf4j.LoggerFactory

/**
 * Handles mob stat initialization on spawn/load.
 *
 * Applies:
 * - Base stats (EntityStatData)
 * - Damage resistances (EntityDamageData)
 * - Stat effects (speed, etc.)
 */
object MobStatsHandler {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")
    private val SOURCE_ID = Identifier("boundbyfate-core", "mob_bestiary")

    private var directoryCreated = false

    fun register() {
        ServerEntityEvents.ENTITY_LOAD.register { entity, world ->
            if (entity is MobEntity && world is ServerWorld) {
                onMobLoad(entity, world)
            }
        }
    }

    private fun onMobLoad(mob: MobEntity, world: ServerWorld) {
        try {
            val worldDir = getWorldDir(world) ?: return
            val mobTypeId = net.minecraft.registry.Registries.ENTITY_TYPE.getId(mob.type)

            // Ensure directory exists (first mob only)
            if (!directoryCreated) {
                directoryCreated = true
            }

            val profile = MobStatConfigLoader.load(worldDir, mobTypeId) ?: return

            // Apply base stats
            val validatedStats = profile.baseStats.mapValues { (statId, value) ->
                StatRegistry.get(statId)?.clamp(value) ?: value
            }
            val statsData = EntityStatData.fromBaseStats(validatedStats)
            mob.setAttached(BbfAttachments.ENTITY_STATS, statsData)

            // Apply resistances from bestiary
            for ((damageTypeId, level) in profile.resistances) {
                DamageResistanceSystem.addResistance(mob, SOURCE_ID, damageTypeId, level)
            }

            // Apply stat effects (speed, etc.)
            StatEffectProcessor.applyAll(mob, statsData)

            logger.debug("Applied bestiary profile to ${mobTypeId} (CR ${profile.challengeRating})")
        } catch (e: Exception) {
            // Silently ignore - mob stats are optional
        }
    }

    private fun getWorldDir(world: ServerWorld) = try {
        val stateManager = world.persistentStateManager
        val directoryField = stateManager.javaClass.getDeclaredField("directory")
        directoryField.isAccessible = true
        (directoryField.get(stateManager) as java.io.File).toPath().parent
    } catch (e: Exception) {
        val server = world.server
        val runDir = server.runDirectory.toPath()
        if (server.isDedicated) runDir.resolve(server.saveProperties.levelName)
        else runDir.resolve("saves").resolve(server.saveProperties.levelName)
    }
}
