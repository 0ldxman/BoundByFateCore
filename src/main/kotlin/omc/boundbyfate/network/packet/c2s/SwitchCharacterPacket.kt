package omc.boundbyfate.network.packet.c2s

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket
import java.util.UUID

/**
 * Пакет от клиента с запросом на переключение персонажа.
 *
 * Отправляется когда:
 * - Игрок выбирает другого персонажа в GUI
 * - Игрок использует команду переключения
 */
class SwitchCharacterPacket(
    /**
     * UUID персонажа, на которого нужно переключиться.
     */
    val characterId: UUID
) : BbfPacket {

    companion object {
        val TYPE: PacketType<SwitchCharacterPacket> = PacketType.create(
            BbfPackets.SWITCH_CHARACTER_C2S
        ) { buf ->
            SwitchCharacterPacket(buf.readUuid())
        }
    }

    override fun getType(): PacketType<SwitchCharacterPacket> = TYPE

    override fun write(buf: PacketByteBuf) {
        buf.writeUuid(characterId)
    }
}
