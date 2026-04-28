package omc.boundbyfate.network.packet.s2c

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleType
import net.minecraft.registry.Registries
import net.minecraft.util.math.Vec3d
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket

/**
 * Пакет для спавна партиклов на клиенте.
 *
 * Сервер вычисляет позиции (линия, круг, сфера и т.д.) и отправляет
 * готовый список точек. Клиент просто вызывает [world.addParticle] для каждой.
 *
 * @param particle тип партикла с параметрами (например DustParticleEffect с цветом)
 * @param positions список позиций для спавна
 * @param velocityX скорость по X
 * @param velocityY скорость по Y
 * @param velocityZ скорость по Z
 * @param speed общая скорость (множитель)
 * @param force если true — показывать даже за пределами дистанции рендера
 */
class SpawnParticlesPacket(
    val particle: ParticleEffect,
    val positions: List<Vec3d>,
    val velocityX: Double = 0.0,
    val velocityY: Double = 0.0,
    val velocityZ: Double = 0.0,
    val speed: Float = 0.0f,
    val force: Boolean = false
) : BbfPacket {

    companion object {
        val TYPE: PacketType<SpawnParticlesPacket> = PacketType.create(
            BbfPackets.SPAWN_PARTICLES_S2C
        ) { buf ->
            // Десериализуем ParticleEffect
            @Suppress("UNCHECKED_CAST")
            val type = Registries.PARTICLE_TYPE.get(buf.readIdentifier())
                    as? ParticleType<ParticleEffect>
                    ?: throw IllegalStateException("Unknown particle type")
            val particle = type.parametersCodec.decode(buf)

            // Список позиций
            val count = buf.readVarInt()
            val positions = ArrayList<Vec3d>(count)
            repeat(count) {
                positions.add(Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()))
            }

            SpawnParticlesPacket(
                particle = particle,
                positions = positions,
                velocityX = buf.readDouble(),
                velocityY = buf.readDouble(),
                velocityZ = buf.readDouble(),
                speed = buf.readFloat(),
                force = buf.readBoolean()
            )
        }
    }

    override fun getType(): PacketType<SpawnParticlesPacket> = TYPE

    override fun write(buf: PacketByteBuf) {
        // Сериализуем ParticleEffect через Minecraft нативный механизм
        @Suppress("UNCHECKED_CAST")
        val type = particle.type as ParticleType<ParticleEffect>
        buf.writeIdentifier(Registries.PARTICLE_TYPE.getId(type))
        type.parametersCodec.encode(buf, particle)

        // Список позиций
        buf.writeVarInt(positions.size)
        for (pos in positions) {
            buf.writeDouble(pos.x)
            buf.writeDouble(pos.y)
            buf.writeDouble(pos.z)
        }

        buf.writeDouble(velocityX)
        buf.writeDouble(velocityY)
        buf.writeDouble(velocityZ)
        buf.writeFloat(speed)
        buf.writeBoolean(force)
    }
}
