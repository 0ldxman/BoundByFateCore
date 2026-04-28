package omc.boundbyfate.network.packet.s2c

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket
import omc.boundbyfate.system.visual.sound.MusicState

/**
 * S2C: сервер рассылает текущее состояние музыки всем клиентам.
 *
 * Отправляется:
 * - При обновлении ползунка от ГМ (throttle 100мс)
 * - При назначении трека на слот
 * - При подключении нового игрока (чтобы синхронизировать состояние)
 *
 * @param state текущее состояние музыкальной системы
 */
class MusicStatePacket(
    val state: MusicState
) : BbfPacket {

    companion object {
        val TYPE: PacketType<MusicStatePacket> = PacketType.create(
            BbfPackets.MUSIC_STATE_S2C
        ) { buf ->
            MusicStatePacket(MusicState(
                trackA = readNullableString(buf),
                trackB = readNullableString(buf),
                trackC = readNullableString(buf),
                u = buf.readFloat(),
                v = buf.readFloat(),
                w = buf.readFloat(),
                sliderSpeed = buf.readFloat()
            ))
        }

        private fun writeNullableString(buf: PacketByteBuf, value: String?) {
            buf.writeBoolean(value != null)
            if (value != null) buf.writeString(value)
        }

        private fun readNullableString(buf: PacketByteBuf): String? {
            val hasValue = buf.readBoolean()
            return if (hasValue) buf.readString() else null
        }
    }

    override fun getType(): PacketType<MusicStatePacket> = TYPE

    override fun write(buf: PacketByteBuf) {
        val s = state
        writeNullableString(buf, s.trackA)
        writeNullableString(buf, s.trackB)
        writeNullableString(buf, s.trackC)
        buf.writeFloat(s.u)
        buf.writeFloat(s.v)
        buf.writeFloat(s.w)
        buf.writeFloat(s.sliderSpeed)
    }

    private fun writeNullableString(buf: PacketByteBuf, value: String?) {
        buf.writeBoolean(value != null)
        if (value != null) buf.writeString(value)
    }
}
