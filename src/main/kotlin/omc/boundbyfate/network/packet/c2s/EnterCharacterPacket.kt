package omc.boundbyfate.network.packet.c2s

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket
import java.util.UUID

/**
 * C2S: игрок запрашивает вход в персонажа.
 *
 * Отправляется когда игрок выбирает персонажа в меню выбора.
 * Сервер проверяет права и выполняет переход.
 */
class EnterCharacterPacket(val characterId: UUID) : BbfPacket {

    companion object {
        val TYPE: PacketType<EnterCharacterPacket> = PacketType.create(
            BbfPackets.ENTER_CHARACTER_C2S
        ) { buf -> EnterCharacterPacket(buf.readUuid()) }
    }

    override fun getType(): PacketType<EnterCharacterPacket> = TYPE
    override fun write(buf: PacketByteBuf) { buf.writeUuid(characterId) }
}
