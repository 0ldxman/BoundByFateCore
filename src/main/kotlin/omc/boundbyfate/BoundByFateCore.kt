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

	override fun onInitialize() {		logger.info("BoundByFate Core initializing...")
		
		// Register stats
		BbfStats.register()
		logger.info("Registered ${StatRegistry.size} stats")
		
		// Register skills and saving throws
		omc.boundbyfate.registry.BbfSkills.register()
		logger.info("Registered ${omc.boundbyfate.registry.SkillRegistry.size} skills/saves")
		
		// Register resource definitions
		omc.boundbyfate.registry.BbfResources.register()
		logger.info("Registered ${omc.boundbyfate.registry.ResourceRegistry.size} resource types")
		
		// Register class datapack loader
		omc.boundbyfate.config.ClassDatapackLoader.register()
		
		// Register built-in penalty effect types
		registerPenaltyEffects()
		
		// Register proficiency datapack loader
		omc.boundbyfate.config.ProficiencyDatapackLoader.register()
		
		// Register feat datapack loader
		omc.boundbyfate.config.FeatDatapackLoader.register()
		
		// Register data attachments
		BbfAttachments.register()
		
		// Register event handlers
		PlayerStatsHandler.register()
		omc.boundbyfate.event.MobStatsHandler.register()
		omc.boundbyfate.event.BlockInteractionHandler.register()
		
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

	private fun registerPenaltyEffects() {
		val registry = omc.boundbyfate.registry.PenaltyEffectRegistry
		
		registry.register(net.minecraft.util.Identifier("boundbyfate-core", "attack_damage")) { params ->
			omc.boundbyfate.system.proficiency.penalty.AttackDamagePenalty(
				params.get("multiplier")?.asFloat ?: 0.5f
			)
		}
		
		registry.register(net.minecraft.util.Identifier("boundbyfate-core", "attack_chance")) { params ->
			omc.boundbyfate.system.proficiency.penalty.AttackChancePenalty(
				params.get("missChance")?.asFloat ?: 0.4f
			)
		}
		
		registry.register(net.minecraft.util.Identifier("boundbyfate-core", "block_interaction")) { _ ->
			omc.boundbyfate.system.proficiency.penalty.BlockInteractionPenalty
		}
		
		logger.info("Registered built-in penalty effect types")
	}
}