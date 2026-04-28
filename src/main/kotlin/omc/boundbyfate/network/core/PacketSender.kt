package omc.boundbyfate.network.core

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import org.slf4j.LoggerFactory

/**
 * Простая и эффективная система отправки пакетов.
 * 
 * Возможности:
 * - Отправка одиночных пакетов
 * - Групповая отправка (broadcast)
 * - Отправка в радиусе
 * 
 * Для батчинга используйте PacketExtensions.syncBatch() или SyncBatchPacket напрямую.
 */
object PacketSender {
    private val logger = LoggerFactory.getLogger(PacketSender::class.java)
    
    // ========== Отправка одиночных пакетов ==========
    
    /**
     * Отправляет пакет игроку.
     * 
     * @param player получатель
     * @param packet пакет для отправки
     */
    fun send(player: ServerPlayerEntity, packet: BbfPacket) {
        try {
            ServerPlayNetworking.send(player, packet)
            logger.debug("Sent packet ${packet.id} to ${player.name.string}")
        } catch (e: Exception) {
            logger.error("Failed to send packet ${packet.id} to ${player.name.string}", e)
        }
    }
    
    // ========== Групповая отправка ==========
    
    /**
     * Отправляет пакет всем игрокам в мире.
     */
    fun broadcast(world: ServerWorld, packet: BbfPacket) {
        for (player in world.players) {
            send(player, packet)
        }
    }
    
    /**
     * Отправляет пакет всем игрокам в радиусе.
     */
    fun broadcastInRadius(
        world: ServerWorld,
        x: Double,
        y: Double,
        z: Double,
        radius: Double,
        packet: BbfPacket
    ) {
        for (player in world.players) {
            val distance = player.squaredDistanceTo(x, y, z)
            if (distance <= radius * radius) {
                send(player, packet)
            }
        }
    }
    
    /**
     * Отправляет пакет всем игрокам кроме исключённого.
     */
    fun broadcastExcept(world: ServerWorld, except: ServerPlayerEntity, packet: BbfPacket) {
        for (player in world.players) {
            if (player != except) {
                send(player, packet)
            }
        }
    }
}
