package omc.boundbyfate.network.packet.s2c

import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import net.minecraft.registry.Registries
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
class PlaySoundPacket(
    val sound: SoundEvent,
    val category: SoundCategory,
    val x: Double,
    val y: Double,
    val z: Double,
    val volume: Float = 1.0f,
    val pitch: Float = 1.0f,
    val positional: Boolean = true
) : BbfPacket {

    companion object {
        val TYPE: PacketType<PlaySoundPacket> = PacketType.create(
            BbfPackets.PLAY_SOUND_S2C
        ) { buf ->
            val soundId = buf.readIdentifier()
            val sound = Registries.SOUND_EVENT.get(soundId)
                ?: throw IllegalStateException("Unknown sound event: $soundId")
            PlaySoundPacket(
                sound = sound,
                category = buf.readEnumConstant(SoundCategory::class.java),
                x = buf.readDouble(),
                y = buf.readDouble(),
                z = buf.readDouble(),
                volume = buf.readFloat(),
                pitch = buf.readFloat(),
                positional = buf.readBoolean()
            )
        }
    }

    override fun getType(): PacketType<PlaySoundPacket> = TYPE

    override fun write(buf: PacketByteBuf) {
        buf.writeIdentifier(Registries.SOUND_EVENT.getId(sound))
        buf.writeEnumConstant(category)
        buf.writeDouble(x)
        buf.writeDouble(y)
        buf.writeDouble(z)
        buf.writeFloat(volume)
        buf.writeFloat(pitch)
        buf.writeBoolean(positional)
    }
}
