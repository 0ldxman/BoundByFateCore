package omc.boundbyfate.network.packet.s2c

import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket
import omc.boundbyfate.network.core.PacketDirection
import omc.boundbyfate.network.core.RegisterPacket

/**
 * Пакет для синхронизации компонента персонажа с клиентом.
 * 
 * Отправляется когда:
 * - Компонент изменился (SyncMode.ON_CHANGE)
 * - Игрок входит на сервер (SyncMode.ON_JOIN)
 * - Каждый тик (SyncMode.EVERY_TICK)
 * 
 * Использует NBT для сериализации компонента через Codec.
 */
@RegisterPacket(
    id = "sync_component",
    direction = PacketDirection.S2C
)
data class SyncComponentPacket(
    /**
     * ID сущности, чей компонент синхронизируется.
     */
    val entityId: Int,
    
    /**
     * ID компонента.
     */
    val componentId: Identifier,
    
    /**
     * Сериализованные данные компонента (NBT в байтах).
     */
    val data: ByteArray
) : BbfPacket {
    
    companion object {
        val ID: CustomPayload.Id<SyncComponentPacket> = 
            CustomPayload.Id(BbfPackets.SYNC_COMPONENT_S2C)
        
        /**
         * Codec для сериализации/десериализации пакета.
         */
        val CODEC: PacketCodec<RegistryByteBuf, SyncComponentPacket> = PacketCodec.tuple(
            PacketCodecs.VAR_INT, SyncComponentPacket::entityId,
            Identifier.PACKET_CODEC, SyncComponentPacket::componentId,
            PacketCodecs.BYTE_ARRAY, SyncComponentPacket::data,
            { entityId, componentId, data -> SyncComponentPacket(entityId, componentId, data) }
        )
    }
    
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SyncComponentPacket) return false
        
        if (entityId != other.entityId) return false
        if (componentId != other.componentId) return false
        if (!data.contentEquals(other.data)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = entityId
        result = 31 * result + componentId.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
