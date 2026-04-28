package omc.boundbyfate.network.packet.c2s

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket
import java.util.UUID

/**
 * C2S: один чанк файла от ГМ на сервер.
 *
 * @param sessionId ID сессии загрузки
 * @param chunkIndex порядковый номер чанка (0-based)
 * @param data байты чанка (до [FileTransferConfig.CHUNK_SIZE_BYTES])
 */
class FileUploadChunkPacket(
    val sessionId: UUID,
    val chunkIndex: Int,
    val data: ByteArray
) : BbfPacket {

    companion object {
        val TYPE: PacketType<FileUploadChunkPacket> = PacketType.create(
            BbfPackets.FILE_UPLOAD_CHUNK_C2S
        ) { buf ->
            FileUploadChunkPacket(
                sessionId = buf.readUuid(),
                chunkIndex = buf.readVarInt(),
                data = buf.readByteArray()
            )
        }
    }

    override fun getType(): PacketType<FileUploadChunkPacket> = TYPE

    override fun write(buf: PacketByteBuf) {
        buf.writeUuid(sessionId)
        buf.writeVarInt(chunkIndex)
        buf.writeByteArray(data)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileUploadChunkPacket) return false
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
