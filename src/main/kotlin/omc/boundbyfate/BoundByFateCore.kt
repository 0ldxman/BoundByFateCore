package omc.boundbyfate

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import omc.boundbyfate.command.LevelCommand
import omc.boundbyfate.command.StatsCommand
import omc.boundbyfate.event.PlayerStatsHandler
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfStats
import omc.boundbyfate.registry.StatRegistry
import org.slf4j.LoggerFactory

object BoundByFateCore : ModInitializer {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

	override fun onInitialize() {
		logger.info("BoundByFate Core initializing...")
		
		// Register stats
		BbfStats.register()
		logger.info("Registered ${StatRegistry.size} stats")
		
		// Register skills and saving throws
		omc.boundbyfate.registry.BbfSkills.register()
		logger.info("Registered ${omc.boundbyfate.registry.SkillRegistry.size} skills/saves")
		
		// Register resource definitions
		omc.boundbyfate.registry.BbfResources.register()
		logger.info("Registered ${omc.boundbyfate.registry.ResourceRegistry.size} resource types")
		
		// Register data attachments
		BbfAttachments.register()
		
		// Register event handlers
		PlayerStatsHandler.register()
		omc.boundbyfate.event.MobStatsHandler.register()
		
		// Periodic cleanup of expired pending check requests
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register {
			omc.boundbyfate.system.check.PendingCheckStore.onServerTick()
		}
		
		logger.info("Registered event handlers")
		
		// Register commands
		CommandRegistrationCallback.EVENT.register { dispatcher, registryAccess, environment ->
			LevelCommand.register(dispatcher, registryAccess, environment)
			StatsCommand.register(dispatcher, registryAccess, environment)
			omc.boundbyfate.command.SkillCheckCommand.register(dispatcher, registryAccess, environment)
			omc.boundbyfate.command.RollCommand.register(dispatcher, registryAccess, environment)
		}
		
		logger.info("BoundByFate Core initialized!")
	}
}