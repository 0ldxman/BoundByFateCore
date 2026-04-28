package omc.boundbyfate.network.packet.s2c

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket
import java.util.UUID

/**
 * Пакет для синхронизации активного персонажа с клиентом.
 *
 * Отправляется когда:
 * - Игрок переключает персонажа
 * - Игрок входит на сервер
 *
 * Легковесный пакет для быстрого обновления активного персонажа.
 */
class SyncActiveCharacterPacket(
    /**
     * UUID активного персонажа.
     * Null если нет активного персонажа.
     */
    val characterId: UUID?
) : BbfPacket {

    companion object {
        val TYPE: PacketType<SyncActiveCharacterPacket> = PacketType.create(
            BbfPackets.SYNC_ACTIVE_CHARACTER_S2C
        ) { buf ->
            val hasId = buf.readBoolean()
            val characterId = if (hasId) buf.readUuid() else null
            SyncActiveCharacterPacket(characterId)
        }
    }

    override fun getType(): PacketType<SyncActiveCharacterPacket> = TYPE

    override fun write(buf: PacketByteBuf) {
        buf.writeBoolean(characterId != null)
        if (characterId != null) buf.writeUuid(characterId)
    }
}
