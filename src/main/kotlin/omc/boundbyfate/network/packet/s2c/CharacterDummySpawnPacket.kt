package omc.boundbyfate.network.packet.s2c

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket
import java.util.UUID

/**
 * S2C: создать CharacterDummy на клиенте.
 *
 * Отправляется всем игрокам когда персонаж переходит в состояние
 * "тело в мире" (игрок вышел из персонажа или из игры).
 *
 * Клиент создаёт [omc.boundbyfate.client.character.CharacterDummy] —
 * entity которая выглядит как игрок с нужным скином и анимацией отдыха.
 *
 * @param characterId UUID персонажа (используется как ключ для despawn)
 * @param skinId ID скина из FileTransferSystem (пустая строка = дефолтный)
 * @param modelType тип модели ("steve" или "alex")
 * @param x, y, z позиция в мире
 * @param yaw поворот
 * @param animationType тип анимации отдыха
 */
class CharacterDummySpawnPacket(
    val characterId: UUID,
    val skinId: String,
    val modelType: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val animationType: DummyAnimationType
) : BbfPacket {

    companion object {
        val TYPE: PacketType<CharacterDummySpawnPacket> = PacketType.create(
            BbfPackets.CHARACTER_DUMMY_SPAWN_S2C
        ) { buf ->
            CharacterDummySpawnPacket(
                characterId = buf.readUuid(),
                skinId = buf.readString(),
                modelType = buf.readString(),
                x = buf.readDouble(),
                y = buf.readDouble(),
                z = buf.readDouble(),
                yaw = buf.readFloat(),
                animationType = buf.readEnumConstant(DummyAnimationType::class.java)
            )
        }
    }

    override fun getType(): PacketType<CharacterDummySpawnPacket> = TYPE

    override fun write(buf: PacketByteBuf) {
        buf.writeUuid(characterId)
        buf.writeString(skinId)
        buf.writeString(modelType)
        buf.writeDouble(x)
        buf.writeDouble(y)
        buf.writeDouble(z)
        buf.writeFloat(yaw)
        buf.writeEnumConstant(animationType)
    }
}

/**
 * Тип анимации отдыха для CharacterDummy.
 *
 * Определяется сервером на основе контекста выхода:
 * - [SLEEP] — рядом есть кровать
 * - [SIT] — игрок был в sneaking или сидел
 * - [STAND_IDLE] — дефолт
 */
enum class DummyAnimationType {
    SLEEP,
    SIT,
    STAND_IDLE
}
