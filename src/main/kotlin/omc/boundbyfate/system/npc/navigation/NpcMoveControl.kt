package omc.boundbyfate.system.npc.navigation

import net.minecraft.entity.ai.control.MoveControl
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.util.math.MathHelper
import omc.boundbyfate.entity.NpcEntity
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Yarn-compatible move control for NPC steering and jumping.
 */
class NpcMoveControl(npc: NpcEntity) : MoveControl(npc) {

    override fun tick() {
        when (state) {
            State.STRAFE -> {
                val speedAttr = entity.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED)
                val movement = (this.speed * speedAttr).toFloat()
                var forward = forwardMovement
                var sideways = sidewaysMovement

                var norm = MathHelper.sqrt(forward * forward + sideways * sideways)
                if (norm < 1.0f) norm = 1.0f
                val scale = movement / norm
                forward *= scale
                sideways *= scale

                entity.movementSpeed = movement
                entity.forwardSpeed = forwardMovement
                entity.sidewaysSpeed = sidewaysMovement
                state = State.WAIT
            }

            State.MOVE_TO -> {
                state = State.WAIT
                val dx = targetX - entity.x
                val dz = targetZ - entity.z
                val dy = targetY - entity.y
                val distSq = dx * dx + dy * dy + dz * dz
                if (distSq < 2.5e-7) {
                    entity.forwardSpeed = 0f
                    return
                }

                val targetYaw = (Math.toDegrees(atan2(dz, dx)) - 90.0).toFloat()
                entity.yaw = wrapDegrees(entity.yaw, targetYaw, 35f)
                entity.movementSpeed = (speed * entity.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED)).toFloat()

                val horizontal = sqrt(dx * dx + dz * dz)
                if (dy > entity.stepHeight && horizontal < entity.width + 1.0f) {
                    entity.jumpControl.setActive()
                    state = State.JUMPING
                }
            }

            State.JUMPING -> {
                entity.movementSpeed = (speed * entity.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED)).toFloat()
                if (entity.isOnGround) {
                    state = State.WAIT
                }
            }

            else -> entity.forwardSpeed = 0f
        }
    }
}

