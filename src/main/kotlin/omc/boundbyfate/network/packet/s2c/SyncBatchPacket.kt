package omc.boundbyfate.network.packet.s2c

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import net.minecraft.util.Identifier
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket

/**
 * Пакет для батч-синхронизации нескольких компонентов за раз.
 *
 * Используется для оптимизации:
 * - При входе игрока (синхронизация всех компонентов)
 * - При переключении персонажа (синхронизация новых компонентов)
 * - При массовых изменениях (левелап, применение эффектов)
 */
class SyncBatchPacket(
    /**
     * ID сущности, чьи компоненты синхронизируются.
     */
    val entityId: Int,

    /**
     * Список компонентов для синхронизации.
     * Каждый элемент: (ID компонента, сериализованные данные)
     */
    val components: List<ComponentData>
) : BbfPacket {

    /**
     * Данные одного компонента в батче.
     */
    class ComponentData(
        val componentId: Identifier,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ComponentData) return false

            if (componentId != other.componentId) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = componentId.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    companion object {
        val TYPE: PacketType<SyncBatchPacket> = PacketType.create(
            BbfPackets.SYNC_BATCH_S2C
        ) { buf ->
            val entityId = buf.readVarInt()
            val count = buf.readVarInt()
            val components = ArrayList<ComponentData>(count)
            repeat(count) {
                components.add(ComponentData(
                    componentId = buf.readIdentifier(),
                    data = buf.readByteArray()
                ))
            }
            SyncBatchPacket(entityId, components)
        }
    }

    override fun getType(): PacketType<SyncBatchPacket> = TYPE

    override fun write(buf: PacketByteBuf) {
        buf.writeVarInt(entityId)
        buf.writeVarInt(components.size)
        for (component in components) {
            buf.writeIdentifier(component.componentId)
            buf.writeByteArray(component.data)
        }
    }
}
