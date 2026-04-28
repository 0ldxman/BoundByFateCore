package omc.boundbyfate.network.packet.c2s

import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket
import omc.boundbyfate.network.core.PacketDirection
import omc.boundbyfate.network.core.RegisterPacket

/**
 * Пакет от клиента с запросом на создание нового персонажа.
 * 
 * Отправляется когда:
 * - Игрок создаёт нового персонажа через GUI
 * - Игрок использует команду создания персонажа
 * 
 * Содержит базовые данные для создания персонажа:
 * - Имя персонажа
 * - Дополнительные данные (NBT в байтах)
 */
@RegisterPacket(
    id = "create_character",
    direction = PacketDirection.C2S
)
data class CreateCharacterPacket(
    /**
     * Имя нового персонажа.
     */
    val name: String,
    
    /**
     * Дополнительные данные персонажа (класс, раса, статы и т.д.).
     * Сериализованы в NBT для гибкости.
     */
    val data: ByteArray
) : BbfPacket {
    
    companion object {
        val ID: CustomPayload.Id<CreateCharacterPacket> = 
            CustomPayload.Id(BbfPackets.CREATE_CHARACTER_C2S)
        
        val CODEC: PacketCodec<RegistryByteBuf, CreateCharacterPacket> = PacketCodec.tuple(
            PacketCodecs.STRING, CreateCharacterPacket::name,
            PacketCodecs.BYTE_ARRAY, CreateCharacterPacket::data,
            ::CreateCharacterPacket
        )
    }
    
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CreateCharacterPacket) return false
        
        if (name != other.name) return false
        if (!data.contentEquals(other.data)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
