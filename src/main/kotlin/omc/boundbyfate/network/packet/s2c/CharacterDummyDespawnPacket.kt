package omc.boundbyfate.network.packet.s2c

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket
import java.util.UUID

/**
 * S2C: убрать CharacterDummy с клиента.
 *
 * Отправляется всем игрокам когда персонаж снова получает активного
 * контроллера (игрок вошёл в персонажа).
 */
class CharacterDummyDespawnPacket(val characterId: UUID) : BbfPacket {

    companion object {
        val TYPE: PacketType<CharacterDummyDespawnPacket> = PacketType.create(
            BbfPackets.CHARACTER_DUMMY_DESPAWN_S2C
        ) { buf -> CharacterDummyDespawnPacket(buf.readUuid()) }
    }

    override fun getType(): PacketType<CharacterDummyDespawnPacket> = TYPE
    override fun write(buf: PacketByteBuf) { buf.writeUuid(characterId) }
}
