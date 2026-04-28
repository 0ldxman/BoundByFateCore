package omc.boundbyfate.network.packet.s2c

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import net.minecraft.util.Identifier
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket

/**
 * Пакет для синхронизации одной секции WorldData с клиентом.
 *
 * Отправляется адресно — только нужным игрокам согласно [SyncStrategy] секции.
 *
 * @param sectionId идентификатор секции
 * @param data сериализованные данные секции (NBT в байтах)
 */
class SyncSectionPacket(
    val sectionId: Identifier,
    val data: ByteArray
) : BbfPacket {

    companion object {
        val TYPE: PacketType<SyncSectionPacket> = PacketType.create(
            BbfPackets.SYNC_SECTION_S2C
        ) { buf ->
            SyncSectionPacket(
                sectionId = buf.readIdentifier(),
                data = buf.readByteArray()
            )
        }
    }

    override fun getType(): PacketType<SyncSectionPacket> = TYPE

    override fun write(buf: PacketByteBuf) {
        buf.writeIdentifier(sectionId)
        buf.writeByteArray(data)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SyncSectionPacket) return false
        return sectionId == other.sectionId && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = sectionId.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
