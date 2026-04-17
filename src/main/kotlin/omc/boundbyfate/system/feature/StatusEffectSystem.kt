package omc.boundbyfate.system.feature

import net.minecraft.entity.LivingEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.api.effect.BbfEffectContext
import omc.boundbyfate.api.feature.FeatureEffectConfig
import omc.boundbyfate.component.EntityFeatureData
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfEffectRegistry
import omc.boundbyfate.registry.FeatureRegistry
import org.slf4j.LoggerFactory

/**
 * Manages BbfStatusEffect application, ticking, and expiry.
 */
object StatusEffectSystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

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

        if (definition.onApply.isNotEmpty()) {
            applyEffects(definition.onApply, buildContext(entity, statusId))
        }

        logger.debug("Applied status $statusId to ${entity.name.string} for ${duration}t")
    }

    fun removeStatus(entity: LivingEntity, statusId: Identifier) {
        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, null) ?: return
        if (!data.hasStatus(statusId)) return

        val definition = FeatureRegistry.getStatus(statusId)
        if (definition?.onRemove?.isNotEmpty() == true) {
            applyEffects(definition.onRemove, buildContext(entity, statusId))
        }

        entity.setAttached(BbfAttachments.ENTITY_FEATURES, data.withoutStatus(statusId))
    }

    fun tick(entity: LivingEntity) {
        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, null) ?: return
        if (data.activeStatuses.isEmpty() && data.cooldowns.isEmpty()) return

        val expired = data.getExpiredStatuses()
        val ticked = data.tick()
        entity.setAttached(BbfAttachments.ENTITY_FEATURES, ticked)

        for (statusId in expired) {
            val definition = FeatureRegistry.getStatus(statusId) ?: continue
            if (definition.onExpire.isNotEmpty()) {
                applyEffects(definition.onExpire, buildContext(entity, statusId))
            }
            entity.setAttached(
                BbfAttachments.ENTITY_FEATURES,
                entity.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, EntityFeatureData())
                    .withoutStatus(statusId)
            )
        }

        for ((statusId, status) in ticked.activeStatuses) {
            val definition = FeatureRegistry.getStatus(statusId) ?: continue
            if (definition.onTick.isEmpty()) continue
            val ticksElapsed = if (status.isPermanent) 0
                else (definition.durationTicks - status.remainingTicks)
            if (ticksElapsed % definition.tickInterval == 0) {
                applyEffects(definition.onTick, buildContext(entity, statusId))
            }
        }
    }

    private fun applyEffects(effectConfigs: List<FeatureEffectConfig>, context: BbfEffectContext) {
        for (config in effectConfigs) {
            val effect = BbfEffectRegistry.create(config.type, config.params) ?: run {
                logger.warn("Unknown effect type: ${config.type}")
                continue
            }
            if (effect.canApply(context)) effect.apply(context)
        }
    }

    private fun buildContext(entity: LivingEntity, sourceId: Identifier): BbfEffectContext {
        val world = entity.world as net.minecraft.server.world.ServerWorld
        val statsData = entity.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
        val level = if (entity is ServerPlayerEntity) {
            entity.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, null)?.level ?: 1
        } else 1

        return BbfEffectContext(
            source = entity,
            targets = listOf(entity),
            targetPos = entity.pos,
            world = world,
            sourceId = sourceId,
            sourceLevel = level,
            sourceStats = statsData
        )
    }
}
