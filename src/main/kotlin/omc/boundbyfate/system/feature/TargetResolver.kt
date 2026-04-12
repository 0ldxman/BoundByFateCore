package omc.boundbyfate.system.feature

import net.minecraft.entity.LivingEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import omc.boundbyfate.api.feature.TargetFilter
import omc.boundbyfate.api.feature.TargetingMode
import kotlin.math.cos

/**
 * Resolves the list of targets for a feature based on targeting mode and filter.
 */
object TargetResolver {

    fun resolve(
        caster: LivingEntity,
        world: ServerWorld,
        mode: TargetingMode,
        filter: TargetFilter,
        range: Float,
        explicitTarget: LivingEntity? = null
    ): List<LivingEntity> {
        return when (mode) {
            is TargetingMode.Self -> listOf(caster)

            is TargetingMode.SingleTarget -> {
                val target = explicitTarget ?: findClosestTarget(caster, world, range, filter)
                if (target != null && matchesFilter(caster, target, filter)) listOf(target)
                else emptyList()
            }

            is TargetingMode.Sphere -> {
                findInSphere(caster, world, caster.pos, mode.radius, filter)
            }

            is TargetingMode.TargetedSphere -> {
                val center = explicitTarget?.pos ?: caster.pos
                findInSphere(caster, world, center, mode.radius, filter)
            }

            is TargetingMode.Cylinder -> {
                findInCylinder(caster, world, mode.radius, mode.height, filter)
            }

            is TargetingMode.Cone -> {
                findInCone(caster, world, mode.length, mode.angleDegrees, filter)
            }

            is TargetingMode.Line -> {
                findInLine(caster, world, mode.length, mode.width, filter)
            }
        }
    }

    private fun findInSphere(
        caster: LivingEntity,
        world: ServerWorld,
        center: Vec3d,
        radius: Float,
        filter: TargetFilter
    ): List<LivingEntity> {
        val box = Box.of(center, radius * 2.0, radius * 2.0, radius * 2.0)
        return world.getEntitiesByClass(LivingEntity::class.java, box) { entity ->
            entity != caster &&
            entity.squaredDistanceTo(center) <= (radius * radius) &&
            matchesFilter(caster, entity, filter)
        }
    }

    private fun findInCylinder(
        caster: LivingEntity,
        world: ServerWorld,
        radius: Float,
        height: Float,
        filter: TargetFilter
    ): List<LivingEntity> {
        val pos = caster.pos
        val box = Box(
            pos.x - radius, pos.y, pos.z - radius,
            pos.x + radius, pos.y + height, pos.z + radius
        )
        return world.getEntitiesByClass(LivingEntity::class.java, box) { entity ->
            entity != caster &&
            entity.x.let { ex -> (ex - pos.x) * (ex - pos.x) } +
            entity.z.let { ez -> (ez - pos.z) * (ez - pos.z) } <= radius * radius &&
            matchesFilter(caster, entity, filter)
        }
    }

    private fun findInCone(
        caster: LivingEntity,
        world: ServerWorld,
        length: Float,
        angleDegrees: Float,
        filter: TargetFilter
    ): List<LivingEntity> {
        val pos = caster.pos
        val facing = caster.rotationVector
        val halfAngleCos = cos(Math.toRadians(angleDegrees / 2.0))
        val box = Box.of(pos, length * 2.0, length * 2.0, length * 2.0)

        return world.getEntitiesByClass(LivingEntity::class.java, box) { entity ->
            if (entity == caster) return@getEntitiesByClass false
            val toEntity = entity.pos.subtract(pos).normalize()
            val dot = facing.dotProduct(toEntity)
            dot >= halfAngleCos && entity.squaredDistanceTo(pos) <= length * length &&
            matchesFilter(caster, entity, filter)
        }
    }

    private fun findInLine(
        caster: LivingEntity,
        world: ServerWorld,
        length: Float,
        width: Float,
        filter: TargetFilter
    ): List<LivingEntity> {
        val pos = caster.pos
        val facing = caster.rotationVector
        val end = pos.add(facing.multiply(length.toDouble()))
        val box = Box.of(pos.add(end).multiply(0.5), length.toDouble(), width.toDouble(), length.toDouble())

        return world.getEntitiesByClass(LivingEntity::class.java, box) { entity ->
            entity != caster && matchesFilter(caster, entity, filter)
            // TODO: more precise line intersection check
        }
    }

    private fun findClosestTarget(
        caster: LivingEntity,
        world: ServerWorld,
        range: Float,
        filter: TargetFilter
    ): LivingEntity? {
        val box = Box.of(caster.pos, range * 2.0, range * 2.0, range * 2.0)
        return world.getEntitiesByClass(LivingEntity::class.java, box) { entity ->
            entity != caster && matchesFilter(caster, entity, filter)
        }.minByOrNull { it.squaredDistanceTo(caster) }
    }

    private fun matchesFilter(caster: LivingEntity, target: LivingEntity, filter: TargetFilter): Boolean {
        return when (filter) {
            TargetFilter.ALL -> true
            TargetFilter.LIVING -> target.isAlive
            TargetFilter.SELF_ONLY -> target == caster
            TargetFilter.ENEMIES -> target != caster // TODO: team/faction system
            TargetFilter.ALLIES -> target == caster  // TODO: team/faction system
        }
    }
}
