package omc.boundbyfate.network.packet.s2c

import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
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
data class FileSyncListPacket(
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
    data class FileMetadata(
        val fileId: String,
        val category: FileCategory,
        val extension: String,
        val totalSize: Long
    )

    companion object {
        val ID: CustomPayload.Id<FileSyncListPacket> =
            CustomPayload.Id(BbfPackets.FILE_SYNC_LIST_S2C)

        val CODEC: PacketCodec<RegistryByteBuf, FileSyncListPacket> = PacketCodec.of(
            { buf, packet ->
                buf.writeVarInt(packet.files.size)
                for (file in packet.files) {
                    buf.writeString(file.fileId)
                    buf.writeEnumConstant(file.category)
                    buf.writeString(file.extension)
                    buf.writeLong(file.totalSize)
                }
            },
            { buf ->
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
        )
    }

    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
}
