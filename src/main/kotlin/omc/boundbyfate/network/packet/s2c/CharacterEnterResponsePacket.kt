package omc.boundbyfate.network.packet.s2c

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket
import java.util.UUID

/**
 * S2C: ответ сервера на запрос входа в персонажа.
 *
 * Отправляется только тому игроку который входит.
 * Содержит данные необходимые клиенту для воспроизведения
 * анимации пробуждения после телепорта.
 *
 * @param characterId UUID персонажа
 * @param animationType тип анимации пробуждения (зеркало DummyAnimationType)
 */
class CharacterEnterResponsePacket(
    val characterId: UUID,
    val animationType: DummyAnimationType
) : BbfPacket {

    companion object {
        val TYPE: PacketType<CharacterEnterResponsePacket> = PacketType.create(
            BbfPackets.CHARACTER_ENTER_RESPONSE_S2C
        ) { buf ->
            CharacterEnterResponsePacket(
                characterId = buf.readUuid(),
                animationType = buf.readEnumConstant(DummyAnimationType::class.java)
            )
        }
    }

    override fun getType(): PacketType<CharacterEnterResponsePacket> = TYPE

    override fun write(buf: PacketByteBuf) {
        buf.writeUuid(characterId)
        buf.writeEnumConstant(animationType)
    }
}
