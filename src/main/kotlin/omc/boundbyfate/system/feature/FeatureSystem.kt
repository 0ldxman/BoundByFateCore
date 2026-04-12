package omc.boundbyfate.system.feature

import net.minecraft.entity.LivingEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import omc.boundbyfate.api.feature.FeatureContext
import omc.boundbyfate.api.feature.FeatureType
import omc.boundbyfate.api.feature.TargetingMode
import omc.boundbyfate.component.EntityFeatureData
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.FeatureConditionRegistry
import omc.boundbyfate.registry.FeatureEffectRegistry
import omc.boundbyfate.registry.FeatureRegistry
import omc.boundbyfate.system.resource.ResourceSystem
import org.slf4j.LoggerFactory

/**
 * Core system for executing features.
 *
 * Usage:
 * ```kotlin
 * // Grant a feature to a player
 * FeatureSystem.grantFeature(player, Identifier("boundbyfate-core", "second_wind"))
 *
 * // Execute an active feature
 * FeatureSystem.execute(player, Identifier("boundbyfate-core", "second_wind"))
 *
 * // Apply a passive feature (called on grant)
 * FeatureSystem.applyPassive(player, Identifier("boundbyfate-core", "celestial_resistance"))
 * ```
 */
object FeatureSystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    /**
     * Grants a feature to an entity and applies it if passive.
     */
    fun grantFeature(entity: LivingEntity, featureId: Identifier) {
        val definition = FeatureRegistry.getFeature(featureId) ?: run {
            logger.warn("Unknown feature: $featureId")
            return
        }

        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, EntityFeatureData())
        entity.setAttached(BbfAttachments.ENTITY_FEATURES, data.withFeature(featureId))

        // Apply passive features immediately
        if (definition.type == FeatureType.PASSIVE) {
            val context = buildContext(entity, featureId, emptyList(), null)
            executeEffects(definition.effects, context)
        }

        logger.debug("Granted feature $featureId to ${entity.name.string}")
    }

    /**
     * Removes a feature from an entity.
     */
    fun removeFeature(entity: LivingEntity, featureId: Identifier) {
        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, null) ?: return
        entity.setAttached(BbfAttachments.ENTITY_FEATURES, data.withoutFeature(featureId))
    }

    /**
     * Executes an active feature.
     *
     * @param caster The entity using the feature
     * @param featureId The feature to execute
     * @param explicitTarget Optional explicit target
     * @param targetPos Optional target position (for AoE)
     * @return true if the feature was executed successfully
     */
    fun execute(
        caster: LivingEntity,
        featureId: Identifier,
        explicitTarget: LivingEntity? = null,
        targetPos: Vec3d? = null
    ): Boolean {
        val definition = FeatureRegistry.getFeature(featureId) ?: run {
            logger.warn("Unknown feature: $featureId")
            return false
        }

        val world = caster.world as? net.minecraft.server.world.ServerWorld ?: return false

        // Check cooldown
        val featureData = caster.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, EntityFeatureData())
        if (featureData.isOnCooldown(featureId)) {
            logger.debug("Feature $featureId is on cooldown for ${caster.name.string}")
            return false
        }

        // Resolve targets
        val targets = TargetResolver.resolve(
            caster = caster,
            world = world,
            mode = definition.targeting,
            filter = definition.targetFilter,
            range = definition.range,
            explicitTarget = explicitTarget
        )

        // Build context
        val context = buildContext(caster, featureId, targets, targetPos)

        // Check conditions
        for (conditionConfig in definition.conditions) {
            val condition = FeatureConditionRegistry.create(conditionConfig.type, conditionConfig.params) ?: continue
            val result = condition.check(context)
            val passes = if (conditionConfig.negate) !result else result
            if (!passes) {
                logger.debug("Feature $featureId condition ${conditionConfig.type} failed for ${caster.name.string}")
                return false
            }
        }

        // Consume resource
        if (definition.cost != null && caster is ServerPlayerEntity) {
            if (!ResourceSystem.spend(caster, definition.cost.resourceId, definition.cost.amount)) {
                logger.debug("Feature $featureId: insufficient resource ${definition.cost.resourceId}")
                return false
            }
        }

        // Execute effects
        executeEffects(definition.effects, context)

        // Apply cooldown
        if (definition.cooldownTicks > 0) {
            val updated = featureData.withCooldown(featureId, definition.cooldownTicks)
            caster.setAttached(BbfAttachments.ENTITY_FEATURES, updated)
        }

        logger.debug("Executed feature $featureId for ${caster.name.string} on ${targets.size} targets")
        return true
    }

    /**
     * Fires trigger-based features (ON_HIT, ON_TAKE_DAMAGE, etc.)
     */
    fun fireTrigger(
        entity: LivingEntity,
        trigger: omc.boundbyfate.api.feature.FeatureTrigger,
        target: LivingEntity? = null,
        triggerData: Map<String, Any> = emptyMap()
    ) {
        val featureData = entity.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, null) ?: return
        val world = entity.world as? net.minecraft.server.world.ServerWorld ?: return

        for (featureId in featureData.grantedFeatures) {
            val definition = FeatureRegistry.getFeature(featureId) ?: continue
            if (definition.trigger != trigger) continue

            val targets = if (target != null) listOf(target) else listOf(entity)
            val context = buildContext(entity, featureId, targets, target?.pos, triggerData)

            // Check conditions
            val conditionsMet = definition.conditions.all { condConfig ->
                val condition = FeatureConditionRegistry.create(condConfig.type, condConfig.params)
                    ?: return@all true
                val result = condition.check(context)
                if (condConfig.negate) !result else result
            }

            if (conditionsMet) {
                executeEffects(definition.effects, context)
            }
        }
    }

    private fun executeEffects(
        effectConfigs: List<omc.boundbyfate.api.feature.FeatureEffectConfig>,
        context: FeatureContext
    ) {
        for (config in effectConfigs) {
            val effect = FeatureEffectRegistry.create(config.type, config.params) ?: run {
                logger.warn("Unknown effect type: ${config.type}")
                continue
            }
            if (effect.canApply(context)) {
                effect.apply(context)
            }
        }
    }

    private fun buildContext(
        caster: LivingEntity,
        featureId: Identifier,
        targets: List<LivingEntity>,
        targetPos: Vec3d?,
        triggerData: Map<String, Any> = emptyMap()
    ): FeatureContext {
        val world = caster.world as net.minecraft.server.world.ServerWorld
        val statsData = caster.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
        val level = if (caster is ServerPlayerEntity) {
            caster.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, null)?.level ?: 1
        } else 1

        return FeatureContext(
            caster = caster,
            targets = targets,
            targetPos = targetPos,
            world = world,
            featureId = featureId,
            casterLevel = level,
            casterStats = statsData,
            triggerData = triggerData
        )
    }
}
