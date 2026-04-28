package omc.boundbyfate.network.packet.s2c

import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import omc.boundbyfate.network.BbfPackets

/**
 * Пакет для батч-синхронизации нескольких компонентов за раз.
 * 
 * Используется для оптимизации:
 * - При входе игрока (синхронизация всех компонентов)
 * - При переключении персонажа (синхронизация новых компонентов)
 * - При массовых изменениях (левелап, применение эффектов)
 * 
 * Преимущества:
 * - Меньше сетевых пакетов
 * - Атомарное обновление (все компоненты обновляются одновременно)
 * - Меньше overhead от заголовков пакетов
 */
data class SyncBatchPacket(
    /**
     * ID сущности, чьи компоненты синхронизируются.
     */
    val entityId: Int,
    
    /**
     * Список компонентов для синхронизации.
     * Каждый элемент: (ID компонента, сериализованные данные)
     */
    val components: List<ComponentData>
) : CustomPayload {
    
    /**
     * Данные одного компонента в батче.
     */
    data class ComponentData(
        val componentId: Identifier,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ComponentData) return false
            
            if (componentId != other.componentId) return false
            if (!data.contentEquals(other.data)) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = componentId.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }
    
    companion object {
        val ID: CustomPayload.Id<SyncBatchPacket> = 
            CustomPayload.Id(BbfPackets.SYNC_BATCH_S2C)
        
        /**
         * Codec для ComponentData.
         */
        private val COMPONENT_DATA_CODEC: PacketCodec<RegistryByteBuf, ComponentData> = 
            PacketCodec.tuple(
                Identifier.PACKET_CODEC, ComponentData::componentId,
                PacketCodecs.BYTE_ARRAY, ComponentData::data,
                ::ComponentData
            )
        
        /**
         * Codec для пакета.
         */
        val CODEC: PacketCodec<RegistryByteBuf, SyncBatchPacket> = PacketCodec.tuple(
            PacketCodecs.VAR_INT, SyncBatchPacket::entityId,
            COMPONENT_DATA_CODEC.collect(PacketCodecs.toList()), SyncBatchPacket::components,
            ::SyncBatchPacket
        )
    }
    
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
}
