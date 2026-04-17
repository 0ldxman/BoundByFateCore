package omc.boundbyfate.system.feature

import net.minecraft.entity.LivingEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import omc.boundbyfate.api.effect.BbfEffectContext
import omc.boundbyfate.api.feature.FeatureEffectConfig
import omc.boundbyfate.component.EntityFeatureData
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfEffectRegistry
import omc.boundbyfate.registry.FeatureRegistry
import org.slf4j.LoggerFactory

/**
 * Core system for Features (Особенности).
 *
 * Features are passive properties — they either apply on grant or fire
 * automatically when a game event matches their trigger condition.
 *
 * Usage:
 * ```kotlin
 * // Grant a feature to a player (applies immediately if no trigger)
 * FeatureSystem.grantFeature(player, Identifier("boundbyfate-core", "darkvision"))
 *
 * // Fire an event — all matching triggered features will execute
 * FeatureSystem.fireEvent(entity, "on_critical_hit", mapOf("damage" to 15f))
 * ```
 */
object FeatureSystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    /**
     * Grants a feature to an entity.
     * If the feature has no trigger (ALWAYS), its effects are applied immediately.
     * If the feature grants abilities, those are also granted.
     */
    fun grantFeature(entity: LivingEntity, featureId: Identifier) {
        val definition = FeatureRegistry.getFeature(featureId) ?: run {
            logger.warn("Unknown feature: $featureId")
            return
        }

        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, EntityFeatureData())
        entity.setAttached(BbfAttachments.ENTITY_FEATURES, data.withFeature(featureId))

        // Apply effects immediately for always-on features (no trigger)
        if (!definition.isTriggered && definition.effects.isNotEmpty()) {
            val context = buildContext(entity, featureId, listOf(entity), null)
            executeEffects(definition.effects, context)
        }

        // Grant abilities from this feature (level-gated)
        if (definition.grantsAbilities.isNotEmpty()) {
            val level = getEntityLevel(entity)
            for (grant in definition.grantsAbilities) {
                if (level >= grant.minLevel) {
                    grantAbility(entity, grant.abilityId)
                }
            }
        }

        logger.debug("Granted feature $featureId to ${entity.name.string}")
    }

    /**
     * Removes a feature from an entity.
     * Also cleans up any effects applied by the feature.
     */
    fun removeFeature(entity: LivingEntity, featureId: Identifier) {
        val data = entity.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, null) ?: return
        entity.setAttached(BbfAttachments.ENTITY_FEATURES, data.withoutFeature(featureId))
        
        // Clean up darkvision if this was a darkvision feature
        if (featureId.path.contains("darkvision")) {
            // Remove Night Vision effect
            if (entity is net.minecraft.server.network.ServerPlayerEntity) {
                entity.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.NIGHT_VISION)
            }
            
            // Clear darkvision attachment
            entity.removeAttached(BbfAttachments.DARKVISION)
            
            // Sync to client (rangeFt=0 means disabled)
            if (entity is net.minecraft.server.network.ServerPlayerEntity) {
                val buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create()
                buf.writeInt(0)
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    entity, 
                    omc.boundbyfate.network.BbfPackets.SYNC_DARKVISION, 
                    buf
                )
            }
        }
        
        logger.debug("Removed feature $featureId from ${entity.name.string}")
    }

    /**
     * Fires a game event. All triggered features on the entity whose trigger.event
     * matches [eventId] and whose filters match [eventData] will execute.
     *
     * @param entity The entity whose features are checked
     * @param eventId The event identifier (e.g. "on_critical_hit", "on_hit")
     * @param eventData Key-value data from the event (e.g. "damage" -> 15f)
     * @param target Optional target entity (e.g. the entity that was hit)
     */
    fun fireEvent(
        entity: LivingEntity,
        eventId: String,
        eventData: Map<String, Any> = emptyMap(),
        target: LivingEntity? = null
    ) {
        val featureData = entity.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, null) ?: return

        for (featureId in featureData.grantedFeatures) {
            val definition = FeatureRegistry.getFeature(featureId) ?: continue
            val trigger = definition.trigger ?: continue
            if (trigger.event != eventId) continue

            // Check filters — all filter entries must match eventData
            val filtersMatch = trigger.filter.all { (key, value) ->
                eventData[key]?.toString() == value
            }
            if (!filtersMatch) continue

            val targets = if (target != null) listOf(target) else listOf(entity)
            val context = buildContext(entity, featureId, targets, target?.pos, eventData)
            executeEffects(definition.effects, context)
        }
    }

    /**
     * Re-evaluates level-gated ability grants for a feature.
     * Call this when a player levels up to grant newly unlocked abilities.
     */
    fun reapplyAbilityGrants(entity: LivingEntity) {
        val featureData = entity.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, null) ?: return
        val level = getEntityLevel(entity)

        for (featureId in featureData.grantedFeatures) {
            val definition = FeatureRegistry.getFeature(featureId) ?: continue
            for (grant in definition.grantsAbilities) {
                if (level >= grant.minLevel) {
                    grantAbility(entity, grant.abilityId)
                }
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun executeEffects(
        effectConfigs: List<FeatureEffectConfig>,
        context: BbfEffectContext
    ) {
        for (config in effectConfigs) {
            val effect = BbfEffectRegistry.create(config.type, config.params) ?: run {
                logger.warn("Unknown effect type: ${config.type}")
                continue
            }
            if (effect.canApply(context)) {
                effect.apply(context)
            }
        }
    }

    private fun buildContext(
        source: LivingEntity,
        featureId: Identifier,
        targets: List<LivingEntity>,
        targetPos: Vec3d?,
        eventData: Map<String, Any> = emptyMap()
    ): BbfEffectContext {
        val world = source.world as net.minecraft.server.world.ServerWorld
        val statsData = source.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
        val level = getEntityLevel(source)

        return BbfEffectContext(
            source = source,
            targets = targets,
            targetPos = targetPos,
            world = world,
            sourceId = featureId,
            sourceLevel = level,
            sourceStats = statsData,
            eventData = eventData
        )
    }

    private fun getEntityLevel(entity: LivingEntity): Int {
        if (entity !is ServerPlayerEntity) return 1
        return entity.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, null)?.level ?: 1
    }

    private fun grantAbility(entity: LivingEntity, abilityId: Identifier) {
        // TODO: add to entity's ability hotbar/granted abilities when ability system is ready
        logger.debug("Feature grants ability $abilityId to ${entity.name.string} (hotbar TODO)")
    }
}
