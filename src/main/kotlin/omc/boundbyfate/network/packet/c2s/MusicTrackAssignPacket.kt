package omc.boundbyfate.network.packet.c2s

import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket

/**
 * C2S: ГМ назначает трек на слот (A, B или C).
 *
 * @param slot слот (0 = A, 1 = B, 2 = C)
 * @param trackId ID трека (null = очистить слот)
 */
data class MusicTrackAssignPacket(
    val slot: Int,
    val trackId: String?
) : BbfPacket {

    companion object {
        val ID: CustomPayload.Id<MusicTrackAssignPacket> =
            CustomPayload.Id(BbfPackets.MUSIC_TRACK_ASSIGN_C2S)

        val CODEC: PacketCodec<RegistryByteBuf, MusicTrackAssignPacket> = PacketCodec.of(
            { buf, packet ->
                buf.writeVarInt(packet.slot)
                buf.writeBoolean(packet.trackId != null)
                if (packet.trackId != null) buf.writeString(packet.trackId)
            },
            { buf ->
                val slot = buf.readVarInt()
                val hasTrack = buf.readBoolean()
                val trackId = if (hasTrack) buf.readString() else null
                MusicTrackAssignPacket(slot, trackId)
            }
        )
    }

    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
}
