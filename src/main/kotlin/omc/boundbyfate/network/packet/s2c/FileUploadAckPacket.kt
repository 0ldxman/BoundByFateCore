package omc.boundbyfate.network.packet.s2c

import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket
import java.util.UUID

/**
 * S2C: подтверждение получения чанка сервером.
 *
 * Клиент использует это для flow control — не отправляет следующий чанк
 * пока не получил подтверждение предыдущего.
 *
 * @param sessionId ID сессии
 * @param chunkIndex индекс подтверждённого чанка
 * @param success true если чанк принят, false если ошибка (сессия не найдена и т.д.)
 * @param errorMessage сообщение об ошибке если success == false
 */
data class FileUploadAckPacket(
    val sessionId: UUID,
    val chunkIndex: Int,
    val success: Boolean = true,
    val errorMessage: String = ""
) : BbfPacket {

    companion object {
        val ID: CustomPayload.Id<FileUploadAckPacket> =
            CustomPayload.Id(BbfPackets.FILE_UPLOAD_ACK_S2C)

        val CODEC: PacketCodec<RegistryByteBuf, FileUploadAckPacket> = PacketCodec.of(
            { buf, packet ->
                buf.writeUuid(packet.sessionId)
                buf.writeVarInt(packet.chunkIndex)
                buf.writeBoolean(packet.success)
                buf.writeString(packet.errorMessage)
            },
            { buf ->
                FileUploadAckPacket(
                    sessionId = buf.readUuid(),
                    chunkIndex = buf.readVarInt(),
                    success = buf.readBoolean(),
                    errorMessage = buf.readString()
                )
            }
        )
    }

    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
}
