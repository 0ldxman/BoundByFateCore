package omc.boundbyfate.network.extension

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import omc.boundbyfate.network.core.BbfPacket
import omc.boundbyfate.network.core.PacketSender

/**
 * Extension функции для удобной отправки пакетов.
 */

/**
 * Отправляет пакет этому игроку.
 */
fun ServerPlayerEntity.sendPacket(packet: BbfPacket) {
    PacketSender.send(this, packet)
}

/**
 * Отправляет пакет всем игрокам в мире.
 */
fun ServerWorld.broadcastPacket(packet: BbfPacket) {
    PacketSender.broadcast(this, packet)
}

/**
 * Отправляет пакет всем игрокам в радиусе от точки.
 */
fun ServerWorld.broadcastPacketInRadius(
    x: Double,
    y: Double,
    z: Double,
    radius: Double,
    packet: BbfPacket
) {
    PacketSender.broadcastInRadius(this, x, y, z, radius, packet)
}

/**
 * Отправляет пакет всем игрокам кроме одного.
 */
fun ServerWorld.broadcastPacketExcept(except: ServerPlayerEntity, packet: BbfPacket) {
    PacketSender.broadcastExcept(this, except, packet)
}
