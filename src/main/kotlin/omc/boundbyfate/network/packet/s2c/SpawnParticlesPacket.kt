package omc.boundbyfate.network.packet.s2c

import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleType
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket

/**
 * Пакет для спавна партиклов на клиенте.
 *
 * Сервер вычисляет позиции (линия, круг, сфера и т.д.) и отправляет
 * готовый список точек. Клиент просто вызывает [world.addParticle] для каждой.
 *
 * Использует нативную сериализацию [ParticleEffect] через Minecraft —
 * никакого отдельного Registry не нужно.
 *
 * @param particle тип партикла с параметрами (например DustParticleEffect с цветом)
 * @param positions список позиций для спавна
 * @param velocityX скорость по X
 * @param velocityY скорость по Y
 * @param velocityZ скорость по Z
 * @param speed общая скорость (множитель)
 * @param force если true — показывать даже за пределами дистанции рендера
 */
data class SpawnParticlesPacket(
    val particle: ParticleEffect,
    val positions: List<Vec3d>,
    val velocityX: Double = 0.0,
    val velocityY: Double = 0.0,
    val velocityZ: Double = 0.0,
    val speed: Float = 0.0f,
    val force: Boolean = false
) : BbfPacket {

    companion object {
        val ID: CustomPayload.Id<SpawnParticlesPacket> =
            CustomPayload.Id(BbfPackets.SPAWN_PARTICLES_S2C)

        val CODEC: PacketCodec<RegistryByteBuf, SpawnParticlesPacket> =
            PacketCodec.of(
                { buf, packet ->
                    // Сериализуем ParticleEffect через Minecraft нативный механизм
                    @Suppress("UNCHECKED_CAST")
                    val type = packet.particle.type as ParticleType<ParticleEffect>
                    buf.writeRegistryValue(net.minecraft.registry.Registries.PARTICLE_TYPE, type)
                    type.parametersFactory.codec().encode(buf, packet.particle)

                    // Список позиций
                    buf.writeVarInt(packet.positions.size)
                    for (pos in packet.positions) {
                        buf.writeDouble(pos.x)
                        buf.writeDouble(pos.y)
                        buf.writeDouble(pos.z)
                    }

                    buf.writeDouble(packet.velocityX)
                    buf.writeDouble(packet.velocityY)
                    buf.writeDouble(packet.velocityZ)
                    buf.writeFloat(packet.speed)
                    buf.writeBoolean(packet.force)
                },
                { buf ->
                    // Десериализуем ParticleEffect
                    @Suppress("UNCHECKED_CAST")
                    val type = buf.readRegistryValue(net.minecraft.registry.Registries.PARTICLE_TYPE)
                            as ParticleType<ParticleEffect>
                    val particle = type.parametersFactory.codec().decode(buf)

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
            )
    }

    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
}
