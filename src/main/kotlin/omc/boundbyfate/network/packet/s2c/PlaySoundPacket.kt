package omc.boundbyfate.network.packet.s2c

import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.registry.Registries
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.core.BbfPacket

/**
 * S2C: воспроизвести звук на клиенте.
 *
 * Используется для Environment (позиционные) и GUI (не позиционные) звуков.
 * Работает только с зарегистрированными [SoundEvent].
 *
 * Для позиционного звука — [x], [y], [z] задают позицию в мире.
 * Для GUI звука — позиция игнорируется, используется [SoundCategory.MASTER].
 *
 * @param sound звуковое событие
 * @param category категория звука (влияет на настройки громкости игрока)
 * @param x позиция X (для позиционного звука)
 * @param y позиция Y
 * @param z позиция Z
 * @param volume громкость (1.0 = стандартная)
 * @param pitch высота тона (1.0 = стандартная)
 * @param positional если false — звук воспроизводится без позиционирования (GUI)
 */
data class PlaySoundPacket(
    val sound: RegistryEntry<SoundEvent>,
    val category: SoundCategory,
    val x: Double,
    val y: Double,
    val z: Double,
    val volume: Float = 1.0f,
    val pitch: Float = 1.0f,
    val positional: Boolean = true
) : BbfPacket {

    companion object {
        val ID: CustomPayload.Id<PlaySoundPacket> =
            CustomPayload.Id(BbfPackets.PLAY_SOUND_S2C)

        val CODEC: PacketCodec<RegistryByteBuf, PlaySoundPacket> = PacketCodec.of(
            { buf, packet ->
                buf.writeRegistryEntry(Registries.SOUND_EVENT, packet.sound)
                buf.writeEnumConstant(packet.category)
                buf.writeDouble(packet.x)
                buf.writeDouble(packet.y)
                buf.writeDouble(packet.z)
                buf.writeFloat(packet.volume)
                buf.writeFloat(packet.pitch)
                buf.writeBoolean(packet.positional)
            },
            { buf ->
                PlaySoundPacket(
                    sound = buf.readRegistryEntry(Registries.SOUND_EVENT),
                    category = buf.readEnumConstant(SoundCategory::class.java),
                    x = buf.readDouble(),
                    y = buf.readDouble(),
                    z = buf.readDouble(),
                    volume = buf.readFloat(),
                    pitch = buf.readFloat(),
                    positional = buf.readBoolean()
                )
            }
        )
    }

    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
}
