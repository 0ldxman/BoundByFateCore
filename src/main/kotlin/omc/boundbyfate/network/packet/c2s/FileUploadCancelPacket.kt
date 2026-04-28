package omc.boundbyfate.network.packet.c2s

import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket
import java.util.UUID

/**
 * C2S: ГМ отменяет загрузку файла.
 *
 * @param sessionId ID отменяемой сессии
 */
data class FileUploadCancelPacket(
    val sessionId: UUID
) : BbfPacket {

    companion object {
        val ID: CustomPayload.Id<FileUploadCancelPacket> =
            CustomPayload.Id(BbfPackets.FILE_UPLOAD_CANCEL_C2S)

        val CODEC: PacketCodec<RegistryByteBuf, FileUploadCancelPacket> = PacketCodec.of(
            { buf, packet -> buf.writeUuid(packet.sessionId) },
            { buf -> FileUploadCancelPacket(buf.readUuid()) }
        )
    }

    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
}
