package omc.boundbyfate.network.packet.c2s

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket

/**
 * C2S: игрок запрашивает выход из персонажа в режим наблюдателя.
 *
 * Отправляется после завершения анимации засыпания на клиенте.
 * К этому моменту экран уже затемнён — сервер выполняет переход
 * пока игрок ничего не видит.
 */
class ExitCharacterPacket : BbfPacket {

    companion object {
        val TYPE: PacketType<ExitCharacterPacket> = PacketType.create(
            BbfPackets.EXIT_CHARACTER_C2S
        ) { ExitCharacterPacket() }
    }

    override fun getType(): PacketType<ExitCharacterPacket> = TYPE
    override fun write(buf: PacketByteBuf) { /* нет данных */ }
}
