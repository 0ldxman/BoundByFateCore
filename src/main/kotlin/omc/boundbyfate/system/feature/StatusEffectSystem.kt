package omc.boundbyfate.system.feature

import net.minecraft.entity.LivingEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.component.EntityFeatureData
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.FeatureEffectRegistry
import omc.boundbyfate.registry.FeatureRegistry
import omc.boundbyfate.api.feature.FeatureContext
import org.slf4j.LoggerFactory

/**
 * Manages BbfStatusEffect application, ticking, and expiry.
 */
object StatusEffectSystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    /**
     * Applies a status effect to an entity.
     */
    fun applyStatus(
        entity: LivingEntity,
        statusId: Identifier,
        sourceId: Identifier? = null,
        durationOverride: Int? = null
    ) {
        val definition = FeatureRegistry.getStatus(statusId) ?: run {
            logger.warn("Unknown status effect: $statusId")
            return
        }

        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, EntityFeatureData())

        val duration = durationOverride ?: definition.durationTicks
        val existing = data.getStatus(statusId)

        val newStatus = if (existing != null && definition.stackable) {
            existing.copy(
                stacks = (existing.stacks + 1).coerceAtMost(definition.maxStacks),
                remainingTicks = duration
            )
        } else {
            EntityFeatureData.ActiveStatus(statusId, duration, 1, sourceId)
        }

        entity.setAttached(BbfAttachments.ENTITY_FEATURES, data.withStatus(newStatus))

        // Apply onApply effects
        if (definition.onApply.isNotEmpty()) {
            val context = buildContext(entity, statusId)
            applyEffects(definition.onApply, context)
        }

        logger.debug("Applied status $statusId to ${entity.name.string} for ${duration}t")
    }

    /**
     * Removes a status effect from an entity.
     */
    fun removeStatus(entity: LivingEntity, statusId: Identifier) {
        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, null) ?: return
        if (!data.hasStatus(statusId)) return

        val definition = FeatureRegistry.getStatus(statusId)

        // Apply onRemove effects
        if (definition?.onRemove?.isNotEmpty() == true) {
            val context = buildContext(entity, statusId)
            applyEffects(definition.onRemove, context)
        }

        entity.setAttached(BbfAttachments.ENTITY_FEATURES, data.withoutStatus(statusId))
    }

    /**
     * Called every server tick for an entity.
     * Ticks down durations, fires onTick effects, handles expiry.
     */
    fun tick(entity: LivingEntity) {
        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, null) ?: return
        if (data.activeStatuses.isEmpty() && data.cooldowns.isEmpty()) return

        // Get expired statuses before ticking
        val expired = data.getExpiredStatuses()

        // Tick the data
        val ticked = data.tick()
        entity.setAttached(BbfAttachments.ENTITY_FEATURES, ticked)

        // Handle expired statuses
        for (statusId in expired) {
            val definition = FeatureRegistry.getStatus(statusId) ?: continue
            if (definition.onExpire.isNotEmpty()) {
                val context = buildContext(entity, statusId)
                applyEffects(definition.onExpire, context)
            }
            entity.setAttached(
                BbfAttachments.ENTITY_FEATURES,
                entity.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, EntityFeatureData())
                    .withoutStatus(statusId)
            )
        }

        // Fire onTick effects for active statuses
        for ((statusId, status) in ticked.activeStatuses) {
            val definition = FeatureRegistry.getStatus(statusId) ?: continue
            if (definition.onTick.isEmpty()) continue

            // Check if this tick interval fires
            val ticksElapsed = if (status.isPermanent) 0
                else (definition.durationTicks - status.remainingTicks)
            if (ticksElapsed % definition.tickInterval == 0) {
                val context = buildContext(entity, statusId)
                applyEffects(definition.onTick, context)
            }
        }
    }

    private fun applyEffects(
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

    private fun buildContext(entity: LivingEntity, sourceId: Identifier): FeatureContext {
        val world = entity.world as? net.minecraft.server.world.ServerWorld ?: return FeatureContext(
            caster = entity,
            targets = listOf(entity),
            targetPos = entity.pos,
            world = entity.world as net.minecraft.server.world.ServerWorld,
            featureId = sourceId
        )

        val statsData = entity.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
        val level = if (entity is ServerPlayerEntity) {
            entity.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, null)?.level ?: 1
        } else 1

        return FeatureContext(
            caster = entity,
            targets = listOf(entity),
            targetPos = entity.pos,
            world = world,
            featureId = sourceId,
            casterLevel = level,
            casterStats = statsData
        )
    }
}
