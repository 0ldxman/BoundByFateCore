package omc.boundbyfate

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import omc.boundbyfate.command.LevelCommand
import omc.boundbyfate.registry.BbfAttachments
import org.slf4j.LoggerFactory

object BoundByFateCore : ModInitializer {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

	override fun onInitialize() {
		logger.info("BoundByFate Core initializing...")
		
		// Register data attachments
		BbfAttachments.register()
		
		// Register commands
		CommandRegistrationCallback.EVENT.register { dispatcher, registryAccess, environment ->
			LevelCommand.register(dispatcher, registryAccess, environment)
		}
		
		logger.info("BoundByFate Core initialized!")
	}
}