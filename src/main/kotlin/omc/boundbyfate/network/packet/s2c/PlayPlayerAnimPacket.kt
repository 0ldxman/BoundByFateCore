package omc.boundbyfate.network.packet.s2c

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import net.minecraft.util.Identifier
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket

/**
 * S2C: запустить или остановить анимацию игрока.
 *
 * ## Слои
 *
 * Без слоя ([layer] = null) — основной слой, анимации заменяют друг друга через transition.
 * С именованным слоем — аддитивный слой поверх основного, независимый.
 *
 * ## Использование
 *
 * Основной слой (способность, стойка):
 * ```kotlin
 * PacketSender.broadcast(world, PlayPlayerAnimPacket(
 *     entityId = player.id,
 *     animId = Identifier("boundbyfate-core", "ability_second_wind")
 * ))
 * ```
 *
 * Аддитивный слой (эффект поверх):
 * ```kotlin
 * PacketSender.broadcast(world, PlayPlayerAnimPacket(
 *     entityId = player.id,
 *     animId = Identifier("boundbyfate-core", "concentration_overlay"),
 *     looping = true,
 *     layer = "overlay"
 * ))
 * ```
 *
 * Остановить конкретный слой:
 * ```kotlin
 * PacketSender.broadcast(world, PlayPlayerAnimPacket(
 *     entityId = player.id,
 *     animId = null,
 *     layer = "overlay"
 * ))
 * ```
 *
 * Остановить основной слой:
 * ```kotlin
 * PacketSender.broadcast(world, PlayPlayerAnimPacket(
 *     entityId = player.id,
 *     animId = null
 * ))
 * ```
 *
 * @param entityId  Сетевой ID сущности (не UUID — это `entity.id`)
 * @param animId    Идентификатор анимации, null = остановить слой
 * @param looping   Если true — анимация играет в цикле до явной остановки
 * @param layer     null = основной слой (transition), строка = именованный аддитивный слой
 */
class PlayPlayerAnimPacket(
    val entityId: Int,
    val animId: Identifier?,
    val looping: Boolean = false,
    val layer: String? = null
) : BbfPacket {

    companion object {
        val TYPE: PacketType<PlayPlayerAnimPacket> = PacketType.create(
            BbfPackets.PLAY_PLAYER_ANIM_S2C
        ) { buf ->
            val entityId = buf.readInt()
            val hasAnim = buf.readBoolean()
            val animId = if (hasAnim) buf.readIdentifier() else null
            val looping = buf.readBoolean()
            val hasLayer = buf.readBoolean()
            val layer = if (hasLayer) buf.readString() else null
            PlayPlayerAnimPacket(entityId, animId, looping, layer)
        }
    }

    override fun getType(): PacketType<PlayPlayerAnimPacket> = TYPE

    override fun write(buf: PacketByteBuf) {
        buf.writeInt(entityId)
        buf.writeBoolean(animId != null)
        animId?.let { buf.writeIdentifier(it) }
        buf.writeBoolean(looping)
        buf.writeBoolean(layer != null)
        layer?.let { buf.writeString(it) }
    }
}
