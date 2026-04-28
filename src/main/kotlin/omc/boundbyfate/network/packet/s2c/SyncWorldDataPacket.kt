package omc.boundbyfate.network.packet.s2c

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket

/**
 * Пакет для синхронизации World Data с клиентом.
 *
 * Отправляется когда:
 * - Игрок входит на сервер
 * - World Data изменяется (создание/удаление персонажа)
 *
 * Содержит только данные, необходимые клиенту:
 * - Список персонажей игрока
 * - ID активного персонажа
 * - Базовая информация о персонажах (имя, класс, уровень)
 */
class SyncWorldDataPacket(
    /**
     * Сериализованные данные World Data (NBT в байтах).
     * Содержит только клиентскую часть данных.
     */
    val data: ByteArray
) : BbfPacket {

    companion object {
        val TYPE: PacketType<SyncWorldDataPacket> = PacketType.create(
            BbfPackets.SYNC_WORLD_DATA_S2C
        ) { buf ->
            SyncWorldDataPacket(buf.readByteArray())
        }
    }

    override fun getType(): PacketType<SyncWorldDataPacket> = TYPE

    override fun write(buf: PacketByteBuf) {
        buf.writeByteArray(data)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SyncWorldDataPacket) return false
        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int = data.contentHashCode()
}
