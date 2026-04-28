package omc.boundbyfate.network.packet.s2c

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
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
class FileUploadAckPacket(
    val sessionId: UUID,
    val chunkIndex: Int,
    val success: Boolean = true,
    val errorMessage: String = ""
) : BbfPacket {

    companion object {
        val TYPE: PacketType<FileUploadAckPacket> = PacketType.create(
            BbfPackets.FILE_UPLOAD_ACK_S2C
        ) { buf ->
            FileUploadAckPacket(
                sessionId = buf.readUuid(),
                chunkIndex = buf.readVarInt(),
                success = buf.readBoolean(),
                errorMessage = buf.readString()
            )
        }
    }

    override fun getType(): PacketType<FileUploadAckPacket> = TYPE

    override fun write(buf: PacketByteBuf) {
        buf.writeUuid(sessionId)
        buf.writeVarInt(chunkIndex)
        buf.writeBoolean(success)
        buf.writeString(errorMessage)
    }
}
