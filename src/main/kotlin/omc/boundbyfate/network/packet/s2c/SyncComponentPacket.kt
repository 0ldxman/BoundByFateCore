package omc.boundbyfate.network.packet.s2c

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import net.minecraft.util.Identifier
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket
import omc.boundbyfate.network.core.PacketDirection
import omc.boundbyfate.network.core.RegisterPacket

/**
 * Пакет для синхронизации компонента персонажа с клиентом.
 *
 * Отправляется когда:
 * - Компонент изменился (SyncMode.ON_CHANGE)
 * - Игрок входит на сервер (SyncMode.ON_JOIN)
 * - Каждый тик (SyncMode.EVERY_TICK)
 *
 * Использует NBT для сериализации компонента через Codec.
 */
@RegisterPacket(
    id = "sync_component",
    direction = PacketDirection.S2C
)
class SyncComponentPacket(
    /**
     * ID сущности, чей компонент синхронизируется.
     */
    val entityId: Int,

    /**
     * ID компонента.
     */
    val componentId: Identifier,

    /**
     * Сериализованные данные компонента (NBT в байтах).
     */
    val data: ByteArray
) : BbfPacket {

    companion object {
        val TYPE: PacketType<SyncComponentPacket> = PacketType.create(
            BbfPackets.SYNC_COMPONENT_S2C
        ) { buf ->
            SyncComponentPacket(
                entityId = buf.readVarInt(),
                componentId = buf.readIdentifier(),
                data = buf.readByteArray()
            )
        }
    }

    override fun getType(): PacketType<SyncComponentPacket> = TYPE

    override fun write(buf: PacketByteBuf) {
        buf.writeVarInt(entityId)
        buf.writeIdentifier(componentId)
        buf.writeByteArray(data)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SyncComponentPacket) return false

        if (entityId != other.entityId) return false
        if (componentId != other.componentId) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = entityId
        result = 31 * result + componentId.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
