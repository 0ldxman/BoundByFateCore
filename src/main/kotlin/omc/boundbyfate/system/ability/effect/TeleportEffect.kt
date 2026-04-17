package omc.boundbyfate.system.ability.effect

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.hit.HitResult
import omc.boundbyfate.api.effect.BbfEffect
import omc.boundbyfate.api.effect.BbfEffectContext
import org.slf4j.LoggerFactory

/**
 * Teleports the source entity.
 *
 * JSON params:
 * - mode: String (TO_CURSOR | BEHIND_TARGET | SWAP_WITH_TARGET, default TO_CURSOR)
 * - maxDistance: Float (default 30)
 * - requiresSafeLanding: Boolean (default true)
 */
class TeleportEffect(
    val mode: TeleportMode = TeleportMode.TO_CURSOR,
    val maxDistance: Float = 30f,
    val requiresSafeLanding: Boolean = true
) : BbfEffect {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun apply(context: BbfEffectContext): Boolean {
        val source = context.source

        val targetPos = when (mode) {
            TeleportMode.TO_CURSOR -> {
                if (source is ServerPlayerEntity) {
                    val raycast = source.raycast(maxDistance.toDouble(), 0f, false)
                    if (raycast.type == HitResult.Type.BLOCK) raycast.pos else null
                } else null
            }
            TeleportMode.BEHIND_TARGET -> {
                val target = context.primaryTarget ?: return false
                val direction = target.rotationVector.multiply(-1.0)
                target.pos.add(direction.multiply(2.0))
            }
            TeleportMode.SWAP_WITH_TARGET -> {
                val target = context.primaryTarget ?: return false
                val sourcePos = source.pos
                target.teleport(sourcePos.x, sourcePos.y, sourcePos.z)
                target.pos
            }
        } ?: return false

        if (source.pos.distanceTo(targetPos) > maxDistance) return false

        source.teleport(targetPos.x, targetPos.y, targetPos.z)
        logger.debug("TeleportEffect: teleported ${source.name?.string} to $targetPos")
        return true
    }
}

enum class TeleportMode { TO_CURSOR, BEHIND_TARGET, SWAP_WITH_TARGET }
