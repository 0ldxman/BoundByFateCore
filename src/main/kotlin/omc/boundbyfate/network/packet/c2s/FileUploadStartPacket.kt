package omc.boundbyfate.network.packet.c2s

import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
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
data class FileUploadStartPacket(
    val sessionId: UUID,
    val fileId: String,
    val category: FileCategory,
    val extension: String,
    val totalSize: Long,
    val totalChunks: Int
) : BbfPacket {

    companion object {
        val ID: CustomPayload.Id<FileUploadStartPacket> =
            CustomPayload.Id(BbfPackets.FILE_UPLOAD_START_C2S)

        val CODEC: PacketCodec<RegistryByteBuf, FileUploadStartPacket> = PacketCodec.of(
            { buf, packet ->
                buf.writeUuid(packet.sessionId)
                buf.writeString(packet.fileId)
                buf.writeEnumConstant(packet.category)
                buf.writeString(packet.extension)
                buf.writeLong(packet.totalSize)
                buf.writeVarInt(packet.totalChunks)
            },
            { buf ->
                FileUploadStartPacket(
                    sessionId = buf.readUuid(),
                    fileId = buf.readString(),
                    category = buf.readEnumConstant(FileCategory::class.java),
                    extension = buf.readString(),
                    totalSize = buf.readLong(),
                    totalChunks = buf.readVarInt()
                )
            }
        )
    }

    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
}
