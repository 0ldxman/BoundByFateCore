package omc.boundbyfate.network.packet.s2c

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
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
class FileDistributeChunkPacket(
    val sessionId: UUID,
    val chunkIndex: Int,
    val data: ByteArray
) : BbfPacket {

    companion object {
        val TYPE: PacketType<FileDistributeChunkPacket> = PacketType.create(
            BbfPackets.FILE_DISTRIBUTE_CHUNK_S2C
        ) { buf ->
            FileDistributeChunkPacket(
                sessionId = buf.readUuid(),
                chunkIndex = buf.readVarInt(),
                data = buf.readByteArray()
            )
        }
    }

    override fun getType(): PacketType<FileDistributeChunkPacket> = TYPE

    override fun write(buf: PacketByteBuf) {
        buf.writeUuid(sessionId)
        buf.writeVarInt(chunkIndex)
        buf.writeByteArray(data)
    }

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
