package omc.boundbyfate.network.packet.s2c

import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket
import omc.boundbyfate.system.transfer.FileCategory
import java.util.UUID

/**
 * S2C: сервер начинает раздачу файла клиентам.
 *
 * Отправляется всем подключённым клиентам когда файл успешно загружен,
 * а также новым игрокам при подключении (для каждого файла которого нет в кеше).
 *
 * @param sessionId уникальный ID сессии раздачи
 * @param fileId ID файла
 * @param category категория файла
 * @param extension расширение файла
 * @param totalSize полный размер файла в байтах
 * @param totalChunks общее количество чанков
 */
data class FileDistributeStartPacket(
    val sessionId: UUID,
    val fileId: String,
    val category: FileCategory,
    val extension: String,
    val totalSize: Long,
    val totalChunks: Int
) : BbfPacket {

    companion object {
        val ID: CustomPayload.Id<FileDistributeStartPacket> =
            CustomPayload.Id(BbfPackets.FILE_DISTRIBUTE_START_S2C)

        val CODEC: PacketCodec<RegistryByteBuf, FileDistributeStartPacket> = PacketCodec.of(
            { buf, packet ->
                buf.writeUuid(packet.sessionId)
                buf.writeString(packet.fileId)
                buf.writeEnumConstant(packet.category)
                buf.writeString(packet.extension)
                buf.writeLong(packet.totalSize)
                buf.writeVarInt(packet.totalChunks)
            },
            { buf ->
                FileDistributeStartPacket(
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
