package omc.boundbyfate.network.packet.s2c

import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
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
data class MusicStatePacket(
    val state: MusicState
) : BbfPacket {

    companion object {
        val ID: CustomPayload.Id<MusicStatePacket> =
            CustomPayload.Id(BbfPackets.MUSIC_STATE_S2C)

        val CODEC: PacketCodec<RegistryByteBuf, MusicStatePacket> = PacketCodec.of(
            { buf, packet ->
                val s = packet.state
                // Треки (nullable string)
                writeNullableString(buf, s.trackA)
                writeNullableString(buf, s.trackB)
                writeNullableString(buf, s.trackC)
                // Позиция ползунка
                buf.writeFloat(s.u)
                buf.writeFloat(s.v)
                buf.writeFloat(s.w)
                // Скорость ползунка
                buf.writeFloat(s.sliderSpeed)
            },
            { buf ->
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
        )

        private fun writeNullableString(buf: RegistryByteBuf, value: String?) {
            buf.writeBoolean(value != null)
            if (value != null) buf.writeString(value)
        }

        private fun readNullableString(buf: RegistryByteBuf): String? {
            val hasValue = buf.readBoolean()
            return if (hasValue) buf.readString() else null
        }
    }

    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
}
