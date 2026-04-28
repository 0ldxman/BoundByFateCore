package omc.boundbyfate.network.packet.s2c

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket
import omc.boundbyfate.system.transfer.FileCategory

/**
 * S2C: список всех файлов на сервере — отправляется новому игроку при подключении.
 *
 * Клиент сравнивает список с кешем и запрашивает только отсутствующие файлы.
 * Для запроса клиент инициирует получение через [FileDistributeStartPacket].
 *
 * @param files список метаданных файлов
 */
class FileSyncListPacket(
    val files: List<FileMetadata>
) : BbfPacket {

    /**
     * Метаданные одного файла.
     *
     * @param fileId ID файла
     * @param category категория
     * @param extension расширение
     * @param totalSize размер в байтах
     */
    class FileMetadata(
        val fileId: String,
        val category: FileCategory,
        val extension: String,
        val totalSize: Long
    )

    companion object {
        val TYPE: PacketType<FileSyncListPacket> = PacketType.create(
            BbfPackets.FILE_SYNC_LIST_S2C
        ) { buf ->
            val count = buf.readVarInt()
            val files = ArrayList<FileMetadata>(count)
            repeat(count) {
                files.add(FileMetadata(
                    fileId = buf.readString(),
                    category = buf.readEnumConstant(FileCategory::class.java),
                    extension = buf.readString(),
                    totalSize = buf.readLong()
                ))
            }
            FileSyncListPacket(files)
        }
    }

    override fun getType(): PacketType<FileSyncListPacket> = TYPE

    override fun write(buf: PacketByteBuf) {
        buf.writeVarInt(files.size)
        for (file in files) {
            buf.writeString(file.fileId)
            buf.writeEnumConstant(file.category)
            buf.writeString(file.extension)
            buf.writeLong(file.totalSize)
        }
    }
}
