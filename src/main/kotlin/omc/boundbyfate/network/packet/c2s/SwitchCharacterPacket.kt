package omc.boundbyfate.network.packet.c2s

import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import omc.boundbyfate.network.BbfPackets
import java.util.*

/**
 * Пакет от клиента с запросом на переключение персонажа.
 * 
 * Отправляется когда:
 * - Игрок выбирает другого персонажа в GUI
 * - Игрок использует команду переключения
 */
data class SwitchCharacterPacket(
    /**
     * UUID персонажа, на которого нужно переключиться.
     */
    val characterId: UUID
) : CustomPayload {
    
    companion object {
        val ID: CustomPayload.Id<SwitchCharacterPacket> = 
            CustomPayload.Id(BbfPackets.SWITCH_CHARACTER_C2S)
        
        val CODEC: PacketCodec<RegistryByteBuf, SwitchCharacterPacket> = PacketCodec.tuple(
            PacketCodecs.UUID, SwitchCharacterPacket::characterId,
            ::SwitchCharacterPacket
        )
    }
    
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
}
