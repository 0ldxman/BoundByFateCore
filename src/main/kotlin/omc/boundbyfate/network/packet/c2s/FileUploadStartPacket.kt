package omc.boundbyfate.network.packet.c2s

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket
import omc.boundbyfate.system.transfer.FileCategory
import java.util.UUID

/**
 * C2S: ГМ начинает загрузку файла на сервер.
 *
 * Отправляется первым — сервер создаёт сессию и ждёт чанки.
 *
 * @param sessionId уникальный ID сессии (генерируется на клиенте)
 * @param fileId желаемый ID файла
 * @param category категория файла
 * @param extension расширение ("ogg", "mp3", "wav", "png")
 * @param totalSize полный размер файла в байтах
 * @param totalChunks общее количество чанков
 */
class FileUploadStartPacket(
    val sessionId: UUID,
    val fileId: String,
    val category: FileCategory,
    val extension: String,
    val totalSize: Long,
    val totalChunks: Int
) : BbfPacket {

    companion object {
        val TYPE: PacketType<FileUploadStartPacket> = PacketType.create(
            BbfPackets.FILE_UPLOAD_START_C2S
        ) { buf ->
            FileUploadStartPacket(
                sessionId = buf.readUuid(),
                fileId = buf.readString(),
                category = buf.readEnumConstant(FileCategory::class.java),
                extension = buf.readString(),
                totalSize = buf.readLong(),
                totalChunks = buf.readVarInt()
            )
        }
    }

    override fun getType(): PacketType<FileUploadStartPacket> = TYPE

    override fun write(buf: PacketByteBuf) {
        buf.writeUuid(sessionId)
        buf.writeString(fileId)
        buf.writeEnumConstant(category)
        buf.writeString(extension)
        buf.writeLong(totalSize)
        buf.writeVarInt(totalChunks)
    }
}
