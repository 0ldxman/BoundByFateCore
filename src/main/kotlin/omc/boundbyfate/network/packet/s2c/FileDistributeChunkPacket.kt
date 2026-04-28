package omc.boundbyfate.network.packet.s2c

import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket
import java.util.UUID

/**
 * S2C: один чанк файла от сервера клиенту.
 *
 * @param sessionId ID сессии раздачи
 * @param chunkIndex порядковый номер чанка (0-based)
 * @param data байты чанка
 */
data class FileDistributeChunkPacket(
    val sessionId: UUID,
    val chunkIndex: Int,
    val data: ByteArray
) : BbfPacket {

    companion object {
        val ID: CustomPayload.Id<FileDistributeChunkPacket> =
            CustomPayload.Id(BbfPackets.FILE_DISTRIBUTE_CHUNK_S2C)

        val CODEC: PacketCodec<RegistryByteBuf, FileDistributeChunkPacket> = PacketCodec.of(
            { buf, packet ->
                buf.writeUuid(packet.sessionId)
                buf.writeVarInt(packet.chunkIndex)
                buf.writeByteArray(packet.data)
            },
            { buf ->
                FileDistributeChunkPacket(
                    sessionId = buf.readUuid(),
                    chunkIndex = buf.readVarInt(),
                    data = buf.readByteArray()
                )
            }
        )
    }

    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileDistributeChunkPacket) return false
        return sessionId == other.sessionId &&
               chunkIndex == other.chunkIndex &&
               data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + data.contentHashCode()
        return result
    }
}
