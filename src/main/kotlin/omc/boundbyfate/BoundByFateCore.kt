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
		
		// Register weapon datapack loader
		omc.boundbyfate.config.WeaponDatapackLoader.register()
		
		// Register built-in penalty effect types
		registerPenaltyEffects()

		// Register built-in damage conditions
		registerBuiltinDamageConditions()
		
		// Register proficiency datapack loader
		omc.boundbyfate.config.ProficiencyDatapackLoader.register()
		
		// Register feat datapack loader
		omc.boundbyfate.config.FeatDatapackLoader.register()
		
		// Register race datapack loader
		omc.boundbyfate.config.RaceDatapackLoader.register()
		
		// Register feature/status datapack loader
		omc.boundbyfate.config.FeatureDatapackLoader.register()
		
		// Register ability datapack loader
		omc.boundbyfate.config.AbilityDatapackLoader.register()
		
		// Register built-in feature effects and conditions
		registerBuiltinFeatureEffects()
		registerBuiltinFeatureConditions()
		
		// Register ability effects
		omc.boundbyfate.registry.BbfAbilityEffects.register()
		
		// Register data attachments
		BbfAttachments.register()
		
		// Register network packet handlers
		omc.boundbyfate.network.ServerPacketHandler.register()
		
		// Register event handlers
		PlayerStatsHandler.register()
		omc.boundbyfate.event.MobStatsHandler.register()
		omc.boundbyfate.event.BlockInteractionHandler.register()
		
		// Periodic cleanup of expired pending check requests
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register { server ->
			omc.boundbyfate.system.check.PendingCheckStore.onServerTick()
			// Tick pending scale tasks (race scale applied after player fully loads)
			omc.boundbyfate.system.race.RaceSystem.tickPendingScales(server)
			// Tick status effects for all online players
			server.playerManager.playerList.forEach { player ->
				omc.boundbyfate.system.feature.StatusEffectSystem.tick(player)
				// Tick ability activations
				omc.boundbyfate.system.ability.AbilityActivationSystem.tick(player)
				// Recalculate AC periodically (every 20 ticks = 1 second)
				if (server.ticks % 20 == 0) {
					omc.boundbyfate.system.combat.ArmorClassSystem.recalculate(player)
				}
			}
		}
		
		logger.info("Registered event handlers")
		
		// Register commands
		CommandRegistrationCallback.EVENT.register { dispatcher, registryAccess, environment ->
			LevelCommand.register(dispatcher, registryAccess, environment)
			StatsCommand.register(dispatcher, registryAccess, environment)
			omc.boundbyfate.command.SkillCheckCommand.register(dispatcher, registryAccess, environment)
			omc.boundbyfate.command.RollCommand.register(dispatcher, registryAccess, environment)
			omc.boundbyfate.command.SkinCommand.register(dispatcher, registryAccess, environment)
			omc.boundbyfate.command.GmCommand.register(dispatcher, registryAccess, environment)
			omc.boundbyfate.command.ClassRaceCommand.register(dispatcher, registryAccess, environment)
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
		val reg = omc.boundbyfate.registry.BbfEffectRegistry
		val id = { path: String -> net.minecraft.util.Identifier("boundbyfate-core", path) }

		reg.register(id("heal")) { params ->
			omc.boundbyfate.system.effect.HealEffect(
				diceCount = params.get("diceCount")?.asInt ?: 1,
				diceType = omc.boundbyfate.api.dice.DiceType.valueOf(params.get("diceType")?.asString?.uppercase() ?: "D8"),
				bonusStatId = params.get("bonusStat")?.asString?.let { net.minecraft.util.Identifier(it) },
				bonusFlat = params.get("bonusFlat")?.asInt ?: 0,
				bonusLevel = params.get("bonusLevel")?.asBoolean ?: false,
				overhealAsTemp = params.get("overhealAsTemp")?.asBoolean ?: false
			)
		}

		reg.register(id("damage")) { params ->
			omc.boundbyfate.system.effect.DamageEffect(
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
			omc.boundbyfate.system.effect.AddResistanceEffect(
				damageTypeId = net.minecraft.util.Identifier(params.get("damageType")?.asString ?: "boundbyfate-core:force"),
				level = params.get("level")?.asInt ?: -1,
				sourceIdOverride = params.get("sourceId")?.asString?.let { net.minecraft.util.Identifier(it) }
			)
		}

		reg.register(id("apply_status")) { params ->
			omc.boundbyfate.system.effect.ApplyStatusEffect(
				statusId = net.minecraft.util.Identifier(params.get("statusId")?.asString ?: ""),
				durationOverride = params.get("durationOverride")?.asInt
			)
		}

		reg.register(id("play_sound")) { params ->
			omc.boundbyfate.system.effect.PlaySoundEffect(
				soundId = net.minecraft.util.Identifier(params.get("sound")?.asString ?: "minecraft:entity.player.levelup"),
				volume = params.get("volume")?.asFloat ?: 1.0f,
				pitch = params.get("pitch")?.asFloat ?: 1.0f
			)
		}

		reg.register(id("spawn_particles")) { params ->
			omc.boundbyfate.system.effect.SpawnParticlesEffect(
				particleId = net.minecraft.util.Identifier(params.get("particle")?.asString ?: "minecraft:heart"),
				count = params.get("count")?.asInt ?: 10,
				spread = params.get("spread")?.asFloat ?: 0.5f,
				speed = params.get("speed")?.asFloat ?: 0.1f,
				onSource = params.get("onCaster")?.asBoolean ?: true,
				onTargets = params.get("onTargets")?.asBoolean ?: false
			)
		}

		reg.register(id("apply_minecraft_effect")) { params ->
			omc.boundbyfate.system.effect.ApplyMinecraftStatusEffect(
				effectId = net.minecraft.util.Identifier(params.get("effect")?.asString ?: "minecraft:regeneration"),
				duration = params.get("duration")?.asInt ?: 200,
				amplifier = params.get("amplifier")?.asInt ?: 0,
				ambient = params.get("ambient")?.asBoolean ?: false,
				showParticles = params.get("showParticles")?.asBoolean ?: true,
				showIcon = params.get("showIcon")?.asBoolean ?: true
			)
		}

		reg.register(id("grant_skill_proficiency")) { params ->
			omc.boundbyfate.system.effect.GrantSkillProficiencyEffect(
				proficiencyId = net.minecraft.util.Identifier(params.get("proficiency")?.asString ?: ""),
				level = omc.boundbyfate.api.skill.ProficiencyLevel.valueOf(
					params.get("level")?.asString?.uppercase() ?: "PROFICIENT"
				)
			)
		}

		reg.register(id("grant_item_proficiency")) { params ->
			omc.boundbyfate.system.effect.GrantItemProficiencyEffect(
				proficiencyId = net.minecraft.util.Identifier(params.get("proficiency")?.asString ?: "")
			)
		}

		reg.register(id("modify_hp_per_level")) { params ->
			omc.boundbyfate.system.effect.ModifyHpPerLevelEffect(
				bonusPerLevel = params.get("bonusPerLevel")?.asInt ?: 1
			)
		}

		reg.register(id("darkvision")) { params ->
			omc.boundbyfate.system.effect.DarkvisionEffect(
				rangeFt = params.get("rangeFt")?.asInt ?: 60
			)
		}

		logger.info("Registered built-in effects")
	}

	private fun registerBuiltinFeatureConditions() {
		val reg = omc.boundbyfate.registry.FeatureConditionRegistry
		val id = { path: String -> net.minecraft.util.Identifier("boundbyfate-core", path) }

		reg.register(id("has_status")) { params ->
			val statusId = net.minecraft.util.Identifier(params.get("statusId")?.asString ?: "")
			omc.boundbyfate.api.feature.FeatureCondition { ctx ->
				ctx.source.getAttachedOrElse(omc.boundbyfate.registry.BbfAttachments.ENTITY_FEATURES, null)
					?.hasStatus(statusId) == true
			}
		}

		reg.register(id("not_has_status")) { params ->
			val statusId = net.minecraft.util.Identifier(params.get("statusId")?.asString ?: "")
			omc.boundbyfate.api.feature.FeatureCondition { ctx ->
				ctx.source.getAttachedOrElse(omc.boundbyfate.registry.BbfAttachments.ENTITY_FEATURES, null)
					?.hasStatus(statusId) != true
			}
		}

		reg.register(id("health_below")) { params ->
			val threshold = params.get("threshold")?.asFloat ?: 0.5f
			omc.boundbyfate.api.feature.FeatureCondition { ctx ->
				ctx.source.health / ctx.source.maxHealth < threshold
			}
		}

		reg.register(id("has_resource")) { params ->
			val resourceId = net.minecraft.util.Identifier(params.get("resource")?.asString ?: "")
			val amount = params.get("amount")?.asInt ?: 1
			omc.boundbyfate.api.feature.FeatureCondition { ctx ->
				if (ctx.source is net.minecraft.server.network.ServerPlayerEntity) {
					omc.boundbyfate.system.resource.ResourceSystem.canSpend(ctx.source, resourceId, amount)
				} else false
			}
		}

		logger.info("Registered built-in feature conditions")
	}

	private fun registerBuiltinDamageConditions() {
		val reg = omc.boundbyfate.registry.DamageConditionRegistry
		val id = { path: String -> net.minecraft.util.Identifier("boundbyfate-core", path) }

		reg.register(id("undead")) { _ ->
			omc.boundbyfate.api.combat.DamageCondition { _, target ->
				target is net.minecraft.entity.mob.ZombieEntity ||
				target is net.minecraft.entity.mob.SkeletonEntity ||
				target is net.minecraft.entity.mob.PhantomEntity ||
				target is net.minecraft.entity.mob.DrownedEntity ||
				target is net.minecraft.entity.mob.ZombieVillagerEntity ||
				target is net.minecraft.entity.mob.WitherSkeletonEntity ||
				target is net.minecraft.entity.mob.StrayEntity ||
				target is net.minecraft.entity.mob.HuskEntity ||
				target is net.minecraft.entity.mob.ZombifiedPiglinEntity ||
				target is net.minecraft.entity.boss.WitherEntity
			}
		}
		reg.registerLabel(id("undead"), "vs Нежить")

		reg.register(id("construct")) { _ ->
			omc.boundbyfate.api.combat.DamageCondition { _, target ->
				target is net.minecraft.entity.passive.IronGolemEntity ||
				target is net.minecraft.entity.passive.SnowGolemEntity
			}
		}
		reg.registerLabel(id("construct"), "vs Конструкты")

		reg.register(id("target_has_status")) { params ->
			val statusId = net.minecraft.util.Identifier(params.get("statusId")?.asString ?: "")
			omc.boundbyfate.api.combat.DamageCondition { _, target ->
				target.getAttachedOrElse(omc.boundbyfate.registry.BbfAttachments.ENTITY_FEATURES, null)
					?.hasStatus(statusId) == true
			}
		}

		reg.register(id("attacker_has_status")) { params ->
			val statusId = net.minecraft.util.Identifier(params.get("statusId")?.asString ?: "")
			omc.boundbyfate.api.combat.DamageCondition { attacker, _ ->
				attacker.getAttachedOrElse(omc.boundbyfate.registry.BbfAttachments.ENTITY_FEATURES, null)
					?.hasStatus(statusId) == true
			}
		}

		reg.register(id("target_health_below")) { params ->
			val threshold = params.get("threshold")?.asFloat ?: 0.5f
			omc.boundbyfate.api.combat.DamageCondition { _, target ->
				target.health / target.maxHealth < threshold
			}
		}

		logger.info("Registered built-in damage conditions")
	}
}