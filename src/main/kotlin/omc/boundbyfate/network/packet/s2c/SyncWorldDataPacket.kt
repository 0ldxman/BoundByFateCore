package omc.boundbyfate.network.packet.s2c

import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import omc.boundbyfate.network.BbfPackets

/**
 * Пакет для синхронизации World Data с клиентом.
 * 
 * Отправляется когда:
 * - Игрок входит на сервер
 * - World Data изменяется (создание/удаление персонажа)
 * 
 * Содержит только данные, необходимые клиенту:
 * - Список персонажей игрока
 * - ID активного персонажа
 * - Базовая информация о персонажах (имя, класс, уровень)
 */
data class SyncWorldDataPacket(
    /**
     * Сериализованные данные World Data (NBT в байтах).
     * Содержит только клиентскую часть данных.
     */
    val data: ByteArray
) : CustomPayload {
    
    companion object {
        val ID: CustomPayload.Id<SyncWorldDataPacket> = 
            CustomPayload.Id(BbfPackets.SYNC_WORLD_DATA_S2C)
        
        val CODEC: PacketCodec<RegistryByteBuf, SyncWorldDataPacket> = PacketCodec.tuple(
            PacketCodecs.BYTE_ARRAY, SyncWorldDataPacket::data,
            ::SyncWorldDataPacket
        )
    }
    
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SyncWorldDataPacket) return false
        return data.contentEquals(other.data)
    }
    
    override fun hashCode(): Int = data.contentHashCode()
}
