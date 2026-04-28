package omc.boundbyfate.network.core

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

/**
 * Регистр для автоматической регистрации пакетов.
 * 
 * Использует аннотацию @RegisterPacket для автоматического
 * обнаружения и регистрации пакетов в Fabric Networking API.
 * 
 * Преимущества:
 * - Автоматическая регистрация через аннотации
 * - Типобезопасность
 * - Централизованное управление
 * - Валидация при регистрации
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
     * @param codec Codec для сериализации
     * @param direction направление пакета
     * @param packetClass класс пакета
     */
    fun <T : BbfPacket> register(
        id: Identifier,
        codec: PacketCodec<RegistryByteBuf, T>,
        direction: PacketDirection,
        packetClass: KClass<T>
    ) {
        // Проверка на дубликаты
        if (registeredPackets.containsKey(id)) {
            throw IllegalStateException("Packet $id is already registered")
        }
        
        // Регистрация в Fabric API
        when (direction) {
            PacketDirection.S2C -> {
                @Suppress("UNCHECKED_CAST")
                PayloadTypeRegistry.playS2C().register(
                    CustomPayload.Id<T>(id),
                    codec as PacketCodec<RegistryByteBuf, out CustomPayload>
                )
            }
            PacketDirection.C2S -> {
                @Suppress("UNCHECKED_CAST")
                PayloadTypeRegistry.playC2S().register(
                    CustomPayload.Id<T>(id),
                    codec as PacketCodec<RegistryByteBuf, out CustomPayload>
                )
            }
            PacketDirection.BOTH -> {
                @Suppress("UNCHECKED_CAST")
                val castedCodec = codec as PacketCodec<RegistryByteBuf, out CustomPayload>
                PayloadTypeRegistry.playS2C().register(CustomPayload.Id<T>(id), castedCodec)
                PayloadTypeRegistry.playC2S().register(CustomPayload.Id<T>(id), castedCodec)
            }
        }
        
        // Сохранение метаданных
        registeredPackets[id] = PacketMetadata(
            id = id,
            direction = direction,
            packetClass = packetClass
        )
        
        logger.info("Registered packet: $id (direction=$direction)")
    }
    
    /**
     * Регистрирует пакет через аннотацию.
     * 
     * @param packetClass класс пакета с аннотацией @RegisterPacket
     * @param codec Codec для сериализации
     */
    fun <T : BbfPacket> registerAnnotated(
        packetClass: KClass<T>,
        codec: PacketCodec<RegistryByteBuf, T>
    ) {
        val annotation = packetClass.findAnnotation<RegisterPacket>()
            ?: throw IllegalArgumentException("Class ${packetClass.simpleName} is not annotated with @RegisterPacket")
        
        val id = Identifier.of("boundbyfate-core", annotation.id)
        
        register(id, codec, annotation.direction, packetClass)
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
