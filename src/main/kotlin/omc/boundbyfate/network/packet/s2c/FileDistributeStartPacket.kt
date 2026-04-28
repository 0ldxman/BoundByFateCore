package omc.boundbyfate.network.packet.s2c

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
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
class FileDistributeStartPacket(
    val sessionId: UUID,
    val fileId: String,
    val category: FileCategory,
    val extension: String,
    val totalSize: Long,
    val totalChunks: Int
) : BbfPacket {

    companion object {
        val TYPE: PacketType<FileDistributeStartPacket> = PacketType.create(
            BbfPackets.FILE_DISTRIBUTE_START_S2C
        ) { buf ->
            FileDistributeStartPacket(
                sessionId = buf.readUuid(),
                fileId = buf.readString(),
                category = buf.readEnumConstant(FileCategory::class.java),
                extension = buf.readString(),
                totalSize = buf.readLong(),
                totalChunks = buf.readVarInt()
            )
        }
    }

    override fun getType(): PacketType<FileDistributeStartPacket> = TYPE

    override fun write(buf: PacketByteBuf) {
        buf.writeUuid(sessionId)
        buf.writeString(fileId)
        buf.writeEnumConstant(category)
        buf.writeString(extension)
        buf.writeLong(totalSize)
        buf.writeVarInt(totalChunks)
    }
}
