package omc.boundbyfate.system.effect

import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.api.effect.BbfEffect
import omc.boundbyfate.api.effect.BbfEffectContext
import omc.boundbyfate.registry.BbfAttachments
import org.slf4j.LoggerFactory

/**
 * Adds a flat bonus to max HP per character level.
 * Applied once on grant — recalculates HP immediately.
 *
 * JSON params:
 * - bonusPerLevel: Int (default 1)
 */
class ModifyHpPerLevelEffect(
    private val bonusPerLevel: Int = 1
) : BbfEffect {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun apply(context: BbfEffectContext): Boolean {
        val player = context.source as? ServerPlayerEntity ?: return false
        val level = context.sourceLevel
        val totalBonus = bonusPerLevel * level

        val attribute = player.getAttributeInstance(
            net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH
        ) ?: return false

        val uuid = java.util.UUID.fromString("bbf00002-0000-0000-0000-000000000001")
        attribute.getModifier(uuid)?.let { attribute.removeModifier(it) }
        attribute.addPersistentModifier(
            net.minecraft.entity.attribute.EntityAttributeModifier(
                uuid, "BoundByFate HP per level bonus",
                totalBonus.toDouble(),
                net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADDITION
            )
        )
        logger.debug("Applied +$totalBonus max HP (${bonusPerLevel}/level × $level) to ${player.name.string}")
        return true
    }
}
