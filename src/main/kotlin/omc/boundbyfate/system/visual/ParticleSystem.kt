package omc.boundbyfate.system.visual

import net.minecraft.particle.ParticleEffect
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Vec3d
import omc.boundbyfate.network.extension.broadcastPacketInRadius
import omc.boundbyfate.network.packet.s2c.SpawnParticlesPacket
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Серверная система партиклов.
 *
 * Отвечает за вычисление позиций и отправку [SpawnParticlesPacket] клиентам.
 * Не хранит никакого состояния — каждый вызов независим.
 *
 * Вызывается через DSL: `visual(world, pos) { particles(...) { ... } }`
 *
 * ## Радиус отправки
 * По умолчанию пакет отправляется игрокам в радиусе 64 блоков от позиции эффекта.
 * Для `force = true` — всем игрокам в мире.
 */
object ParticleSystem {

    /** Радиус по умолчанию для отправки пакетов игрокам. */
    const val DEFAULT_RADIUS = 64.0

    // ── Публичный API ─────────────────────────────────────────────────────

    /**
     * Спавнит партиклы в одной точке.
     */
    fun spawnAt(
        world: ServerWorld,
        particle: ParticleEffect,
        pos: Vec3d,
        count: Int = 1,
        speed: Float = 0.0f,
        velocity: Vec3d = Vec3d.ZERO,
        force: Boolean = false
    ) {
        val positions = List(count) { pos }
        send(world, particle, positions, velocity, speed, force)
    }

    /**
     * Спавнит партиклы линией между двумя точками.
     *
     * ```kotlin
     * particles(ParticleTypes.END_ROD) {
     *     line(from, to, count = 20)
     * }
     * ```
     */
    fun spawnLine(
        world: ServerWorld,
        particle: ParticleEffect,
        from: Vec3d,
        to: Vec3d,
        count: Int = 10,
        speed: Float = 0.0f,
        force: Boolean = false
    ) {
        val positions = buildList(count) {
            for (i in 0 until count) {
                val t = if (count == 1) 0.0 else i.toDouble() / (count - 1)
                add(from.lerp(to, t))
            }
        }
        send(world, particle, positions, Vec3d.ZERO, speed, force)
    }

    /**
     * Спавнит партиклы по кругу в горизонтальной плоскости (XZ).
     *
     * ```kotlin
     * particles(ParticleTypes.FLAME) {
     *     circle(center, radius = 3.0, count = 24)
     * }
     * ```
     */
    fun spawnCircle(
        world: ServerWorld,
        particle: ParticleEffect,
        center: Vec3d,
        radius: Double,
        count: Int = 20,
        speed: Float = 0.0f,
        force: Boolean = false
    ) {
        val positions = buildList(count) {
            val step = 2 * Math.PI / count
            for (i in 0 until count) {
                val angle = i * step
                add(Vec3d(
                    center.x + radius * cos(angle),
                    center.y,
                    center.z + radius * sin(angle)
                ))
            }
        }
        send(world, particle, positions, Vec3d.ZERO, speed, force)
    }

    /**
     * Спавнит партиклы равномерно по поверхности сферы.
     *
     * ```kotlin
     * particles(ParticleTypes.WITCH) {
     *     sphere(center, radius = 2.0, count = 40)
     * }
     * ```
     */
    fun spawnSphere(
        world: ServerWorld,
        particle: ParticleEffect,
        center: Vec3d,
        radius: Double,
        count: Int = 30,
        speed: Float = 0.0f,
        force: Boolean = false
    ) {
        // Алгоритм Фибоначчи для равномерного распределения по сфере
        val positions = buildList(count) {
            val goldenRatio = (1 + sqrt(5.0)) / 2
            for (i in 0 until count) {
                val theta = 2 * Math.PI * i / goldenRatio
                val phi = Math.acos(1 - 2.0 * (i + 0.5) / count)
                add(Vec3d(
                    center.x + radius * sin(phi) * cos(theta),
                    center.y + radius * cos(phi),
                    center.z + radius * sin(phi) * sin(theta)
                ))
            }
        }
        send(world, particle, positions, Vec3d.ZERO, speed, force)
    }

    /**
     * Спавнит партиклы случайно внутри AABB области.
     *
     * ```kotlin
     * particles(ParticleTypes.SMOKE) {
     *     area(min, max, count = 30)
     * }
     * ```
     */
    fun spawnArea(
        world: ServerWorld,
        particle: ParticleEffect,
        min: Vec3d,
        max: Vec3d,
        count: Int = 20,
        speed: Float = 0.0f,
        force: Boolean = false
    ) {
        val rng = world.random
        val positions = buildList(count) {
            repeat(count) {
                add(Vec3d(
                    min.x + rng.nextDouble() * (max.x - min.x),
                    min.y + rng.nextDouble() * (max.y - min.y),
                    min.z + rng.nextDouble() * (max.z - min.z)
                ))
            }
        }
        send(world, particle, positions, Vec3d.ZERO, speed, force)
    }

    /**
     * Спавнит партиклы спиралью вверх.
     *
     * ```kotlin
     * particles(ParticleTypes.PORTAL) {
     *     spiral(center, radius = 1.5, height = 3.0, count = 40)
     * }
     * ```
     */
    fun spawnSpiral(
        world: ServerWorld,
        particle: ParticleEffect,
        center: Vec3d,
        radius: Double,
        height: Double,
        count: Int = 30,
        rotations: Double = 2.0,
        speed: Float = 0.0f,
        force: Boolean = false
    ) {
        val positions = buildList(count) {
            for (i in 0 until count) {
                val t = i.toDouble() / count
                val angle = t * rotations * 2 * Math.PI
                add(Vec3d(
                    center.x + radius * cos(angle),
                    center.y + t * height,
                    center.z + radius * sin(angle)
                ))
            }
        }
        send(world, particle, positions, Vec3d.ZERO, speed, force)
    }

    // ── Внутренняя отправка ───────────────────────────────────────────────

    private fun send(
        world: ServerWorld,
        particle: ParticleEffect,
        positions: List<Vec3d>,
        velocity: Vec3d,
        speed: Float,
        force: Boolean
    ) {
        if (positions.isEmpty()) return

        val packet = SpawnParticlesPacket(
            particle = particle,
            positions = positions,
            velocityX = velocity.x,
            velocityY = velocity.y,
            velocityZ = velocity.z,
            speed = speed,
            force = force
        )

        // Центр эффекта — первая позиция (для определения радиуса отправки)
        val center = positions[0]

        if (force) {
            // force = true → отправляем всем игрокам в мире
            world.broadcastPacketInRadius(
                center.x, center.y, center.z,
                Double.MAX_VALUE,
                packet
            )
        } else {
            world.broadcastPacketInRadius(
                center.x, center.y, center.z,
                DEFAULT_RADIUS,
                packet
            )
        }
    }
}
