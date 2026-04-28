package omc.boundbyfate.network.packet.s2c

import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import omc.boundbyfate.network.BbfPackets
import java.util.*

/**
 * Пакет для синхронизации активного персонажа с клиентом.
 * 
 * Отправляется когда:
 * - Игрок переключает персонажа
 * - Игрок входит на сервер
 * 
 * Легковесный пакет для быстрого обновления активного персонажа.
 */
data class SyncActiveCharacterPacket(
    /**
     * UUID активного персонажа.
     * Null если нет активного персонажа.
     */
    val characterId: UUID?
) : CustomPayload {
    
    companion object {
        val ID: CustomPayload.Id<SyncActiveCharacterPacket> = 
            CustomPayload.Id(BbfPackets.SYNC_ACTIVE_CHARACTER_S2C)
        
        val CODEC: PacketCodec<RegistryByteBuf, SyncActiveCharacterPacket> = PacketCodec.tuple(
            PacketCodecs.optional(PacketCodecs.UUID), SyncActiveCharacterPacket::characterId,
            ::SyncActiveCharacterPacket
        )
    }
    
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
}
