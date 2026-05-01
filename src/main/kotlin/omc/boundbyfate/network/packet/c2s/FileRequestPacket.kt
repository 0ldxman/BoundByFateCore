package omc.boundbyfate.network.packet.c2s

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket
import omc.boundbyfate.system.transfer.FileCategory

/**
 * C2S: клиент запрашивает конкретные файлы с сервера.
 *
 * Отправляется после получения [omc.boundbyfate.network.packet.s2c.FileSyncListPacket]:
 * клиент сравнивает список сервера с локальным кешем и запрашивает только отсутствующие.
 *
 * Сервер в ответ начинает раздачу каждого запрошенного файла через
 * [omc.boundbyfate.network.packet.s2c.FileDistributeStartPacket] +
 * [omc.boundbyfate.network.packet.s2c.FileDistributeChunkPacket].
 *
 * @param files список запрашиваемых файлов
 */
class FileRequestPacket(
    val files: List<FileRequest>
) : BbfPacket {

    /**
     * Запрос одного файла.
     *
     * @param fileId ID файла
     * @param category категория
     * @param extension расширение
     */
    data class FileRequest(
        val fileId: String,
        val category: FileCategory,
        val extension: String
    )

    companion object {
        val TYPE: PacketType<FileRequestPacket> = PacketType.create(
            BbfPackets.FILE_REQUEST_C2S
        ) { buf ->
            val count = buf.readVarInt()
            val files = ArrayList<FileRequest>(count)
            repeat(count) {
                files.add(FileRequest(
                    fileId = buf.readString(),
                    category = buf.readEnumConstant(FileCategory::class.java),
                    extension = buf.readString()
                ))
            }
            FileRequestPacket(files)
        }
    }

    override fun getType(): PacketType<FileRequestPacket> = TYPE

    override fun write(buf: PacketByteBuf) {
        buf.writeVarInt(files.size)
        for (file in files) {
            buf.writeString(file.fileId)
            buf.writeEnumConstant(file.category)
            buf.writeString(file.extension)
        }
    }
}
