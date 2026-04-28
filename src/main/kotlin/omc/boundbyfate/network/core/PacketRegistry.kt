package omc.boundbyfate.network.core

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

/**
 * Регистр для автоматической регистрации пакетов (1.20.1 Fabric API).
 *
 * Использует аннотацию @RegisterPacket для автоматического
 * обнаружения и регистрации пакетов в Fabric Networking API.
 */
object PacketRegistry {
    private val logger = LoggerFactory.getLogger(PacketRegistry::class.java)

    /**
     * Зарегистрированные пакеты.
     * Key: ID пакета, Value: метаданные
     */
    private val registeredPackets: MutableMap<Identifier, PacketMetadata> = mutableMapOf()

    /**
     * Регистрирует пакет вручную.
     *
     * @param id ID пакета
     * @param type PacketType для сериализации
     * @param direction направление пакета
     * @param packetClass класс пакета
     */
    fun <T : BbfPacket> register(
        id: Identifier,
        type: PacketType<T>,
        direction: PacketDirection,
        packetClass: KClass<T>
    ) {
        if (registeredPackets.containsKey(id)) {
            throw IllegalStateException("Packet $id is already registered")
        }

        registeredPackets[id] = PacketMetadata(
            id = id,
            direction = direction,
            packetClass = packetClass
        )

        logger.info("Registered packet: $id (direction=$direction)")
    }

    /**
     * Получает метаданные пакета по ID.
     */
    fun getMetadata(id: Identifier): PacketMetadata? = registeredPackets[id]

    /**
     * Проверяет, зарегистрирован ли пакет.
     */
    fun isRegistered(id: Identifier): Boolean = registeredPackets.containsKey(id)

    /**
     * Возвращает все зарегистрированные пакеты.
     */
    fun getAllPackets(): Collection<PacketMetadata> = registeredPackets.values

    /**
     * Возвращает пакеты определённого направления.
     */
    fun getPacketsByDirection(direction: PacketDirection): List<PacketMetadata> =
        registeredPackets.values.filter { it.direction == direction || it.direction == PacketDirection.BOTH }

    /**
     * Выводит статистику по зарегистрированным пакетам.
     */
    fun printStatistics() {
        logger.info("=== Packet Registry Statistics ===")
        logger.info("Total packets: ${registeredPackets.size}")

        val s2c = getPacketsByDirection(PacketDirection.S2C).size
        val c2s = getPacketsByDirection(PacketDirection.C2S).size

        logger.info("  S2C packets: $s2c")
        logger.info("  C2S packets: $c2s")
        logger.info("===================================")
    }
}

/**
 * Метаданные зарегистрированного пакета.
 */
data class PacketMetadata(
    val id: Identifier,
    val direction: PacketDirection,
    val packetClass: KClass<out BbfPacket>
)
