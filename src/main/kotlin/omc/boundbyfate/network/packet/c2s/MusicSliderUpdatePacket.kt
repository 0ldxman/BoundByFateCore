package omc.boundbyfate.network.packet.c2s

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
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
class MusicSliderUpdatePacket(
    val u: Float,
    val v: Float
) : BbfPacket {

    companion object {
        val TYPE: PacketType<MusicSliderUpdatePacket> = PacketType.create(
            BbfPackets.MUSIC_SLIDER_UPDATE_C2S
        ) { buf ->
            MusicSliderUpdatePacket(buf.readFloat(), buf.readFloat())
        }
    }

    override fun getType(): PacketType<MusicSliderUpdatePacket> = TYPE

    override fun write(buf: PacketByteBuf) {
        buf.writeFloat(u)
        buf.writeFloat(v)
    }
}
