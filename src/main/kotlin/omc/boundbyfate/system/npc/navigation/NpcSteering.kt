package omc.boundbyfate.system.npc.navigation

import kotlinx.coroutines.delay
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.PathAwareEntity
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Yarn-compatible steering helpers for NPC movement and facing.
 */
fun LivingEntity.faceTowards(
    target: Vec3d,
    maxAngularSpeed: Float = 360f,
    updateIntervalMs: Long = 50L,
): Boolean {
    val eye = eyePos
    val dx = target.x - eye.x
    val dy = target.y - eye.y
    val dz = target.z - eye.z
    if (dx * dx + dy * dy + dz * dz < 1.0e-6) return true

    val maxAnglePerUpdate = maxAngularSpeed * (updateIntervalMs / 1000f)
    val targetYaw = Math.toDegrees(atan2(dz, dx)).toFloat() - 90f
    val horizontal = sqrt(dx * dx + dz * dz)
    val targetPitch = -Math.toDegrees(atan2(dy, horizontal)).toFloat().coerceIn(-90f, 90f)

    val yawDiff = MathHelper.wrapDegrees(targetYaw - yaw)
    val pitchDiff = targetPitch - pitch
    yaw += yawDiff.coerceIn(-maxAnglePerUpdate, maxAnglePerUpdate)
    pitch = MathHelper.clamp(pitch + pitchDiff.coerceIn(-maxAnglePerUpdate, maxAnglePerUpdate), -90f, 90f)
    bodyYaw = yaw
    headYaw = yaw

    return abs(yawDiff) <= 1f && abs(pitchDiff) <= 1f
}

fun LivingEntity.faceTowards(
    target: Entity,
    maxAngularSpeed: Float = 360f,
    updateIntervalMs: Long = 50L,
): Boolean = faceTowards(target.pos.add(0.0, target.standingEyeHeight.toDouble(), 0.0), maxAngularSpeed, updateIntervalMs)

fun PathAwareEntity.moveTowards(
    target: Vec3d,
    speed: Double,
    arrivalRadius: Double = 1.5,
): Boolean {
    if (squaredDistanceTo(target) <= arrivalRadius * arrivalRadius) {
        navigation.stop()
        forwardSpeed = 0f
        sidewaysSpeed = 0f
        return true
    }

    faceTowards(target)
    val close = max(arrivalRadius, 1.0)
    if (squaredDistanceTo(target) <= close * close) {
        moveControl.moveTo(target.x, target.y, target.z, speed)
    } else {
        navigation.startMovingTo(target.x, target.y, target.z, speed)
    }
    return false
}

fun PathAwareEntity.moveTowards(
    target: Entity,
    speed: Double,
    arrivalRadius: Double = 1.5,
): Boolean = moveTowards(target.pos.add(0.0, target.standingEyeHeight.toDouble(), 0.0), speed, arrivalRadius)

suspend fun LivingEntity.rotate(
    targetProvider: suspend () -> Vec3d,
    durationMs: Long,
    updateIntervalMs: Long = 50L,
    maxAngularSpeed: Float = 360f,
) {
    val end = System.currentTimeMillis() + durationMs
    while (System.currentTimeMillis() < end) {
        faceTowards(targetProvider(), maxAngularSpeed, updateIntervalMs)
        delay(updateIntervalMs)
    }
}

suspend fun LivingEntity.rotate(
    targetEntity: Entity,
    durationMs: Long,
    updateIntervalMs: Long = 50L,
    maxAngularSpeed: Float = 360f,
) = rotate({ targetEntity.pos.add(0.0, targetEntity.standingEyeHeight.toDouble(), 0.0) }, durationMs, updateIntervalMs, maxAngularSpeed)

