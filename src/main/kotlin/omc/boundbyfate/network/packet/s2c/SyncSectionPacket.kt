package omc.boundbyfate.network.packet.s2c

import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import omc.boundbyfate.network.core.BbfPacket

/**
 * Пакет для синхронизации одной секции WorldData с клиентом.
 *
 * Отправляется адресно — только нужным игрокам согласно [SyncStrategy] секции.
 *
 * @param sectionId идентификатор секции
 * @param data сериализованные данные секции (NBT в байтах)
 */
data class SyncSectionPacket(
    val sectionId: Identifier,
    val data: ByteArray
) : BbfPacket {

    companion object {
        val PACKET_ID = Identifier.of("boundbyfate-core", "sync_section")
        val ID: CustomPayload.Id<SyncSectionPacket> = CustomPayload.Id(PACKET_ID)

        val CODEC: PacketCodec<RegistryByteBuf, SyncSectionPacket> = PacketCodec.tuple(
            Identifier.PACKET_CODEC, SyncSectionPacket::sectionId,
            PacketCodecs.BYTE_ARRAY, SyncSectionPacket::data,
            ::SyncSectionPacket
        )
    }

    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SyncSectionPacket) return false
        return sectionId == other.sectionId && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = sectionId.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
