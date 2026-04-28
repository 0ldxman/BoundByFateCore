package omc.boundbyfate.system.visual.builder

import net.minecraft.particle.ParticleEffect
import net.minecraft.util.math.Vec3d
import omc.boundbyfate.api.visual.VisualContext
import omc.boundbyfate.system.visual.ParticleSystem

/**
 * DSL билдер для подсистемы партиклов.
 *
 * Используется внутри [VisualBuilder.particles].
 * Накапливает вызовы и выполняет их через [ParticleSystem] при вызове [execute].
 *
 * ```kotlin
 * particles(ParticleTypes.END_ROD) {
 *     line(from, to, count = 20)
 *     circle(center, radius = 2.0, count = 24)
 *     sphere(center, radius = 1.5, count = 30)
 *     at(pos, count = 5)
 * }
 * ```
 */
class ParticleBuilder(
    private val particle: ParticleEffect,
    private val ctx: VisualContext
) {
    private val actions = mutableListOf<() -> Unit>()

    /**
     * Спавнит [count] партиклов в одной точке.
     */
    fun at(
        pos: Vec3d = ctx.pos,
        count: Int = 1,
        speed: Float = 0.0f,
        velocity: Vec3d = Vec3d.ZERO,
        force: Boolean = false
    ) {
        actions += {
            ParticleSystem.spawnAt(ctx.world, particle, pos, count, speed, velocity, force)
        }
    }

    /**
     * Спавнит партиклы линией между двумя точками.
     */
    fun line(
        from: Vec3d,
        to: Vec3d,
        count: Int = 10,
        speed: Float = 0.0f,
        force: Boolean = false
    ) {
        actions += {
            ParticleSystem.spawnLine(ctx.world, particle, from, to, count, speed, force)
        }
    }

    /**
     * Спавнит партиклы по кругу в горизонтальной плоскости.
     */
    fun circle(
        center: Vec3d = ctx.pos,
        radius: Double,
        count: Int = 20,
        speed: Float = 0.0f,
        force: Boolean = false
    ) {
        actions += {
            ParticleSystem.spawnCircle(ctx.world, particle, center, radius, count, speed, force)
        }
    }

    /**
     * Спавнит партиклы равномерно по поверхности сферы.
     */
    fun sphere(
        center: Vec3d = ctx.pos,
        radius: Double,
        count: Int = 30,
        speed: Float = 0.0f,
        force: Boolean = false
    ) {
        actions += {
            ParticleSystem.spawnSphere(ctx.world, particle, center, radius, count, speed, force)
        }
    }

    /**
     * Спавнит партиклы случайно внутри AABB области.
     */
    fun area(
        min: Vec3d,
        max: Vec3d,
        count: Int = 20,
        speed: Float = 0.0f,
        force: Boolean = false
    ) {
        actions += {
            ParticleSystem.spawnArea(ctx.world, particle, min, max, count, speed, force)
        }
    }

    /**
     * Спавнит партиклы спиралью вверх.
     */
    fun spiral(
        center: Vec3d = ctx.pos,
        radius: Double,
        height: Double,
        count: Int = 30,
        rotations: Double = 2.0,
        speed: Float = 0.0f,
        force: Boolean = false
    ) {
        actions += {
            ParticleSystem.spawnSpiral(
                ctx.world, particle, center, radius, height, count, rotations, speed, force
            )
        }
    }

    internal fun execute() {
        actions.forEach { it() }
    }
}
