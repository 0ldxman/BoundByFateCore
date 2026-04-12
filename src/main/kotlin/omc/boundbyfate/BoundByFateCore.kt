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
		
		// Register race datapack loader
		omc.boundbyfate.config.RaceDatapackLoader.register()
		
		// Register feature/status datapack loader
		omc.boundbyfate.config.FeatureDatapackLoader.register()
		
		// Register built-in feature effects and conditions
		registerBuiltinFeatureEffects()
		registerBuiltinFeatureConditions()
		
		// Register data attachments
		BbfAttachments.register()
		
		// Register event handlers
		PlayerStatsHandler.register()
		omc.boundbyfate.event.MobStatsHandler.register()
		omc.boundbyfate.event.BlockInteractionHandler.register()
		
		// Periodic cleanup of expired pending check requests
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register { server ->
			omc.boundbyfate.system.check.PendingCheckStore.onServerTick()
			// Tick status effects for all online players
			server.playerManager.playerList.forEach { player ->
				omc.boundbyfate.system.feature.StatusEffectSystem.tick(player)
			}
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

	private fun registerPenaltyEffects() {		val registry = omc.boundbyfate.registry.PenaltyEffectRegistry
		
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

	private fun registerBuiltinFeatureEffects() {
		val reg = omc.boundbyfate.registry.FeatureEffectRegistry
		val id = { path: String -> net.minecraft.util.Identifier("boundbyfate-core", path) }

		reg.register(id("heal")) { params ->
			omc.boundbyfate.system.feature.effect.HealEffect(
				diceCount = params.get("diceCount")?.asInt ?: 1,
				diceType = omc.boundbyfate.api.dice.DiceType.valueOf(params.get("diceType")?.asString?.uppercase() ?: "D8"),
				bonusStatId = params.get("bonusStat")?.asString?.let { net.minecraft.util.Identifier(it) },
				bonusFlat = params.get("bonusFlat")?.asInt ?: 0,
				bonusLevel = params.get("bonusLevel")?.asBoolean ?: false
			)
		}

		reg.register(id("damage")) { params ->
			omc.boundbyfate.system.feature.effect.DamageEffect(
				diceCount = params.get("diceCount")?.asInt ?: 1,
				diceType = omc.boundbyfate.api.dice.DiceType.valueOf(params.get("diceType")?.asString?.uppercase() ?: "D6"),
				damageTypeId = params.get("damageType")?.asString?.let { net.minecraft.util.Identifier(it) }
					?: net.minecraft.util.Identifier("boundbyfate-core", "force"),
				bonusStatId = params.get("bonusStat")?.asString?.let { net.minecraft.util.Identifier(it) },
				bonusFlat = params.get("bonusFlat")?.asInt ?: 0,
				bonusLevel = params.get("bonusLevel")?.asBoolean ?: false
			)
		}

		reg.register(id("add_resistance")) { params ->
			omc.boundbyfate.system.feature.effect.AddResistanceEffect(
				damageTypeId = net.minecraft.util.Identifier(params.get("damageType")?.asString ?: "boundbyfate-core:force"),
				level = params.get("level")?.asInt ?: -1,
				sourceIdOverride = params.get("sourceId")?.asString?.let { net.minecraft.util.Identifier(it) }
			)
		}

		reg.register(id("apply_status")) { params ->
			omc.boundbyfate.system.feature.effect.ApplyStatusEffect(
				statusId = net.minecraft.util.Identifier(params.get("statusId")?.asString ?: ""),
				durationOverride = params.get("durationOverride")?.asInt
			)
		}

		reg.register(id("play_sound")) { params ->
			omc.boundbyfate.system.feature.effect.PlaySoundEffect(
				soundId = net.minecraft.util.Identifier(params.get("sound")?.asString ?: "minecraft:entity.player.levelup"),
				volume = params.get("volume")?.asFloat ?: 1.0f,
				pitch = params.get("pitch")?.asFloat ?: 1.0f
			)
		}

		logger.info("Registered built-in feature effects")
	}

	private fun registerBuiltinFeatureConditions() {
		val reg = omc.boundbyfate.registry.FeatureConditionRegistry
		val id = { path: String -> net.minecraft.util.Identifier("boundbyfate-core", path) }

		reg.register(id("has_status")) { params ->
			val statusId = net.minecraft.util.Identifier(params.get("statusId")?.asString ?: "")
			omc.boundbyfate.api.feature.FeatureCondition { ctx ->
				ctx.caster.getAttachedOrElse(omc.boundbyfate.registry.BbfAttachments.ENTITY_FEATURES, null)
					?.hasStatus(statusId) == true
			}
		}

		reg.register(id("not_has_status")) { params ->
			val statusId = net.minecraft.util.Identifier(params.get("statusId")?.asString ?: "")
			omc.boundbyfate.api.feature.FeatureCondition { ctx ->
				ctx.caster.getAttachedOrElse(omc.boundbyfate.registry.BbfAttachments.ENTITY_FEATURES, null)
					?.hasStatus(statusId) != true
			}
		}

		reg.register(id("health_below")) { params ->
			val threshold = params.get("threshold")?.asFloat ?: 0.5f
			omc.boundbyfate.api.feature.FeatureCondition { ctx ->
				ctx.caster.health / ctx.caster.maxHealth < threshold
			}
		}

		reg.register(id("has_resource")) { params ->
			val resourceId = net.minecraft.util.Identifier(params.get("resource")?.asString ?: "")
			val amount = params.get("amount")?.asInt ?: 1
			omc.boundbyfate.api.feature.FeatureCondition { ctx ->
				if (ctx.caster is net.minecraft.server.network.ServerPlayerEntity) {
					omc.boundbyfate.system.resource.ResourceSystem.canSpend(ctx.caster, resourceId, amount)
				} else false
			}
		}

		logger.info("Registered built-in feature conditions")
	}