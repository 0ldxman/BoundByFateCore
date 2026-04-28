package omc.boundbyfate.network.packet.c2s

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket

/**
 * C2S: ГМ назначает трек на слот (A, B или C).
 *
 * @param slot слот (0 = A, 1 = B, 2 = C)
 * @param trackId ID трека (null = очистить слот)
 */
class MusicTrackAssignPacket(
    val slot: Int,
    val trackId: String?
) : BbfPacket {

    companion object {
        val TYPE: PacketType<MusicTrackAssignPacket> = PacketType.create(
            BbfPackets.MUSIC_TRACK_ASSIGN_C2S
        ) { buf ->
            val slot = buf.readVarInt()
            val hasTrack = buf.readBoolean()
            val trackId = if (hasTrack) buf.readString() else null
            MusicTrackAssignPacket(slot, trackId)
        }
    }

    override fun getType(): PacketType<MusicTrackAssignPacket> = TYPE

    override fun write(buf: PacketByteBuf) {
        buf.writeVarInt(slot)
        buf.writeBoolean(trackId != null)
        if (trackId != null) buf.writeString(trackId)
    }
}
