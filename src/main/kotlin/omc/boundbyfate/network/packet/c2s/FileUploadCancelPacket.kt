package omc.boundbyfate.network.packet.c2s

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket
import java.util.UUID

/**
 * C2S: ГМ отменяет загрузку файла.
 *
 * @param sessionId ID отменяемой сессии
 */
class FileUploadCancelPacket(
    val sessionId: UUID
) : BbfPacket {

    companion object {
        val TYPE: PacketType<FileUploadCancelPacket> = PacketType.create(
            BbfPackets.FILE_UPLOAD_CANCEL_C2S
        ) { buf ->
            FileUploadCancelPacket(buf.readUuid())
        }
    }

    override fun getType(): PacketType<FileUploadCancelPacket> = TYPE

    override fun write(buf: PacketByteBuf) {
        buf.writeUuid(sessionId)
    }
}
