package omc.boundbyfate.network.packet.c2s

import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket

/**
 * C2S: ГМ двигает ползунок треугольника.
 *
 * Отправляется с throttle — не чаще раза в 100мс пока ГМ держит мышку.
 * Сервер рассылает новую позицию всем клиентам через [MusicStatePacket].
 *
 * @param u координата слота A (0.0 - 1.0)
 * @param v координата слота B (0.0 - 1.0)
 * Координата w вычисляется как 1 - u - v.
 */
data class MusicSliderUpdatePacket(
    val u: Float,
    val v: Float
) : BbfPacket {

    companion object {
        val ID: CustomPayload.Id<MusicSliderUpdatePacket> =
            CustomPayload.Id(BbfPackets.MUSIC_SLIDER_UPDATE_C2S)

        val CODEC: PacketCodec<RegistryByteBuf, MusicSliderUpdatePacket> = PacketCodec.of(
            { buf, packet ->
                buf.writeFloat(packet.u)
                buf.writeFloat(packet.v)
            },
            { buf -> MusicSliderUpdatePacket(buf.readFloat(), buf.readFloat()) }
        )
    }

    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
}
