package omc.boundbyfate.system.status

import net.minecraft.entity.LivingEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.api.effect.EffectContext
import omc.boundbyfate.api.status.ActiveStatus
import omc.boundbyfate.api.status.event.AfterStatusApplyEvent
import omc.boundbyfate.api.status.event.AfterStatusRemoveEvent
import omc.boundbyfate.api.status.event.BeforeStatusApplyEvent
import omc.boundbyfate.api.status.event.BeforeStatusRemoveEvent
import omc.boundbyfate.api.status.event.OnStatusDurationUpdateEvent
import omc.boundbyfate.api.status.event.OnStatusExpireEvent
import omc.boundbyfate.api.status.event.StatusEvents
import omc.boundbyfate.component.components.EntityCombatData
import omc.boundbyfate.component.components.EntityStatusesData
import omc.boundbyfate.component.core.getOrCreate
import omc.boundbyfate.registry.EffectRegistry
import omc.boundbyfate.registry.StatusRegistry
import omc.boundbyfate.system.effect.EffectApplier
import omc.boundbyfate.util.source.SourceReference
import omc.boundbyfate.util.time.Duration
import org.slf4j.LoggerFactory

/**
 * Система управления состояниями (Status Conditions).
 */
object StatusSystem {

    private val logger = LoggerFactory.getLogger(StatusSystem::class.java)

    fun apply(
        entity: LivingEntity,
        statusId: Identifier,
        duration: Duration,
        source: SourceReference
    ): Boolean {
        val definition = StatusRegistry.get(statusId) ?: run {
            logger.warn("Status '$statusId' not found in registry")
            return false
        }

        if (hasImmunity(entity, statusId)) {
            logger.debug("Entity '${entity.name.string}' is immune to '$statusId'")
            return false
        }

        val beforeEvent = BeforeStatusApplyEvent(entity, definition, source, duration)
        StatusEvents.BEFORE_APPLY.invokeCancellable(beforeEvent) { it.onBeforeApply(beforeEvent) }

        if (beforeEvent.isCancelled) {
            logger.debug("Status '$statusId' apply cancelled by BEFORE_APPLY on '${entity.name.string}'")
            return false
        }

        val effectiveDuration = beforeEvent.duration
        val currentTick = entity.world.time
        val existing = getActiveStatus(entity, statusId)

        if (existing != null) {
            if (isNewDurationLonger(existing, effectiveDuration, currentTick)) {
                updateDuration(entity, statusId, effectiveDuration, currentTick)
                val updateEvent = OnStatusDurationUpdateEvent(entity, definition, existing, effectiveDuration)
                StatusEvents.ON_DURATION_UPDATE.invoke { it.onDurationUpdate(updateEvent) }
                logger.debug("Updated duration of '$statusId' on '${entity.name.string}'")
            }
            return true
        }

        val activeStatus = ActiveStatus(
            statusId = statusId,
            source = source,
            duration = effectiveDuration,
            appliedAtTick = currentTick
        )

        addToComponent(entity, activeStatus)

        val effectSource = SourceReference.condition(statusId)
        for (effectDef in definition.effects) {
            val handler = EffectRegistry.getHandler(effectDef.id) ?: run {
                logger.warn("Effect handler '${effectDef.id}' not found for status '$statusId'")
                null
            } ?: continue
            val ctx = EffectContext.passive(entity, effectDef, effectSource)
            EffectApplier.apply(handler, ctx)
        }

        for (includedId in definition.includes) {
            apply(entity, includedId, effectiveDuration, source)
        }

        val afterEvent = AfterStatusApplyEvent(entity, definition, activeStatus)
        StatusEvents.AFTER_APPLY.invoke { it.onAfterApply(afterEvent) }

        logger.debug("Applied status '$statusId' to '${entity.name.string}' (duration: $effectiveDuration)")
        return true
    }

    fun remove(
        entity: LivingEntity,
        statusId: Identifier,
        source: SourceReference = SourceReference.admin("system")
    ): Boolean {
        val definition = StatusRegistry.get(statusId) ?: run {
            logger.warn("Status '$statusId' not found in registry")
            return false
        }

        val existing = getActiveStatus(entity, statusId) ?: return false

        val beforeEvent = BeforeStatusRemoveEvent(entity, definition, existing, source)
        StatusEvents.BEFORE_REMOVE.invokeCancellable(beforeEvent) { it.onBeforeRemove(beforeEvent) }

        if (beforeEvent.isCancelled) {
            logger.debug("Status '$statusId' remove cancelled by BEFORE_REMOVE on '${entity.name.string}'")
            return false
        }

        removeFromComponent(entity, statusId)

        val effectSource = SourceReference.condition(statusId)
        for (effectDef in definition.effects) {
            val handler = EffectRegistry.getHandler(effectDef.id) ?: continue
            val ctx = EffectContext.passive(entity, effectDef, effectSource)
            EffectApplier.remove(handler, ctx)
        }

        removeOrphanedIncludes(entity, definition.includes, source)

        val afterEvent = AfterStatusRemoveEvent(entity, definition, source)
        StatusEvents.AFTER_REMOVE.invoke { it.onAfterRemove(afterEvent) }

        logger.debug("Removed status '$statusId' from '${entity.name.string}'")
        return true
    }

    fun removeAll(entity: LivingEntity, source: SourceReference = SourceReference.admin("system")) {
        val activeIds = getActiveStatuses(entity).map { it.statusId }.toList()
        for (statusId in activeIds) {
            remove(entity, statusId, source)
        }
    }

    fun removeByEvent(
        entity: LivingEntity,
        eventId: Identifier,
        source: SourceReference = SourceReference.admin("event:$eventId")
    ) {
        val toRemove = getActiveStatuses(entity)
            .filter { it.isRemovedByEvent(eventId) }
            .map { it.statusId }

        for (statusId in toRemove) {
            remove(entity, statusId, source)
        }

        if (toRemove.isNotEmpty()) {
            logger.debug("Removed ${toRemove.size} statuses from '${entity.name.string}' by event '$eventId'")
        }
    }

    fun tick(entity: LivingEntity, currentTick: Long) {
        val toExpire = mutableListOf<ActiveStatus>()

        for (activeStatus in getActiveStatuses(entity)) {
            if (activeStatus.isExpired(currentTick)) {
                toExpire.add(activeStatus)
                continue
            }
            tickStatusEffects(entity, activeStatus, currentTick)
        }

        for (activeStatus in toExpire) {
            val definition = StatusRegistry.get(activeStatus.statusId) ?: continue

            val expireEvent = OnStatusExpireEvent(entity, definition, activeStatus)
            StatusEvents.ON_EXPIRE.invokeCancellable(expireEvent) { it.onExpire(expireEvent) }

            if (expireEvent.isCancelled) {
                logger.debug("Status '${activeStatus.statusId}' expiry cancelled by ON_EXPIRE")
                continue
            }

            logger.debug("Status '${activeStatus.statusId}' expired on '${entity.name.string}'")
            remove(entity, activeStatus.statusId, SourceReference.admin("expired"))
        }
    }

    fun hasStatus(entity: LivingEntity, statusId: Identifier): Boolean =
        getActiveStatus(entity, statusId) != null

    fun hasImmunity(entity: LivingEntity, statusId: Identifier): Boolean =
        entity.getOrCreate(EntityCombatData.TYPE).isImmuneToStatus(statusId)

    fun getActiveStatuses(entity: LivingEntity): List<ActiveStatus> =
        entity.getOrCreate(EntityStatusesData.TYPE).activeStatuses.toList()

    fun getActiveStatus(entity: LivingEntity, statusId: Identifier): ActiveStatus? =
        getActiveStatuses(entity).find { it.statusId == statusId }

    // ── Внутренняя логика ─────────────────────────────────────────────────

    private fun tickStatusEffects(entity: LivingEntity, activeStatus: ActiveStatus, currentTick: Long) {
        val definition = StatusRegistry.get(activeStatus.statusId) ?: return
        val ticksActive = (currentTick - activeStatus.appliedAtTick).toInt()
        val effectSource = SourceReference.condition(activeStatus.statusId)

        for (effectDef in definition.effects) {
            val handler = EffectRegistry.getHandler(effectDef.id) ?: continue
            if (!handler.isTicking) continue

            val ctx = EffectContext.tick(
                entity = entity,
                definition = effectDef,
                source = effectSource,
                ticksActive = ticksActive,
                stash = activeStatus.stash
            )
            EffectApplier.tick(handler, ctx)
        }
    }

    private fun removeOrphanedIncludes(entity: LivingEntity, includes: List<Identifier>, source: SourceReference) {
        if (includes.isEmpty()) return

        val stillNeeded = mutableSetOf<Identifier>()
        for (activeStatus in getActiveStatuses(entity)) {
            stillNeeded.addAll(StatusRegistry.getAllIncludes(activeStatus.statusId))
        }

        for (includedId in includes) {
            if (includedId !in stillNeeded) {
                remove(entity, includedId, source)
            }
        }
    }

    private fun isNewDurationLonger(existing: ActiveStatus, newDuration: Duration, currentTick: Long): Boolean {
        if (newDuration is Duration.Permanent) return existing.duration !is Duration.Permanent
        if (existing.duration is Duration.Permanent) return false
        if (newDuration is Duration.Ticks && existing.duration is Duration.Ticks) {
            return newDuration.ticks > existing.remainingTicks(currentTick)
        }
        return false
    }

    private fun updateDuration(entity: LivingEntity, statusId: Identifier, newDuration: Duration, currentTick: Long) {
        entity.getOrCreate(EntityStatusesData.TYPE).updateDuration(statusId, newDuration, currentTick)
    }

    private fun addToComponent(entity: LivingEntity, activeStatus: ActiveStatus) {
        entity.getOrCreate(EntityStatusesData.TYPE).addStatus(activeStatus)
    }

    private fun removeFromComponent(entity: LivingEntity, statusId: Identifier) {
        entity.getOrCreate(EntityStatusesData.TYPE).removeStatus(statusId)
    }
}
