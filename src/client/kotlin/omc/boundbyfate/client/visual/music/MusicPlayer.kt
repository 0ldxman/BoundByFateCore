package omc.boundbyfate.client.visual.music

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import org.lwjgl.openal.AL10.*
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

/**
 * Один OpenAL источник звука для музыкального трека.
 *
 * Загружает аудиофайл в OpenAL буфер и воспроизводит его зациклено.
 * Поддерживает форматы: OGG, WAV, MP3 (через jlayer).
 *
 * Работает напрямую через OpenAL — не использует Minecraft sound engine,
 * что позволяет загружать треки в рантайме без регистрации SoundEvent.
 *
 * @param slotName название слота для логов ("A", "B", "C")
 */
@Environment(EnvType.CLIENT)
class MusicPlayer(private val slotName: String) {

    private val logger = LoggerFactory.getLogger("MusicPlayer[$slotName]")

    private var sourceId: Int = AL_NONE
    private var bufferId: Int = AL_NONE
    private var currentTrackId: String? = null

    // ── Публичный API ─────────────────────────────────────────────────────

    /**
     * Загружает трек в OpenAL буфер.
     * Если уже играет другой трек — останавливает его.
     */
    fun load(trackId: String, bytes: ByteArray, extension: String) {
        stop()
        cleanup()

        try {
            val pcmData = decodeToPcm(bytes, extension) ?: run {
                logger.error("Failed to decode track $trackId.$extension")
                return
            }

            // Создаём OpenAL буфер
            bufferId = alGenBuffers()
            // alBufferData ожидает ShortBuffer для 16-bit PCM
            val shortBuffer = java.nio.ByteBuffer.wrap(pcmData.data)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()
            alBufferData(
                bufferId,
                if (pcmData.channels == 1) AL_FORMAT_MONO16 else AL_FORMAT_STEREO16,
                shortBuffer,
                pcmData.sampleRate
            )

            // Создаём OpenAL источник
            sourceId = alGenSources()
            alSourcei(sourceId, AL_BUFFER, bufferId)
            alSourcei(sourceId, AL_LOOPING, AL_TRUE)   // зацикливаем
            alSourcef(sourceId, AL_GAIN, 0f)            // начинаем с нулевой громкости
            alSourcef(sourceId, AL_ROLLOFF_FACTOR, 0f) // без затухания по расстоянию (музыка глобальная)

            currentTrackId = trackId
            logger.info("Loaded track: $trackId.$extension")

        } catch (e: Exception) {
            logger.error("Error loading track $trackId", e)
            cleanup()
        }
    }

    /**
     * Начинает воспроизведение.
     */
    fun play() {
        if (sourceId == AL_NONE) return
        alSourcePlay(sourceId)
        logger.debug("Playing slot $slotName: $currentTrackId")
    }

    /**
     * Останавливает воспроизведение.
     */
    fun stop() {
        if (sourceId == AL_NONE) return
        alSourceStop(sourceId)
        logger.debug("Stopped slot $slotName")
    }

    /**
     * Устанавливает громкость (0.0 - 1.0).
     * Вызывается каждый тик из [MusicClientSystem].
     */
    fun setVolume(volume: Float) {
        if (sourceId == AL_NONE) return
        alSourcef(sourceId, AL_GAIN, volume.coerceIn(0f, 1f))
    }

    // ── Декодирование аудио ───────────────────────────────────────────────

    /**
     * Декодирует аудиофайл в PCM данные для OpenAL.
     */
    private fun decodeToPcm(bytes: ByteArray, extension: String): PcmData? {
        return when (extension.lowercase()) {
            "wav" -> decodeWav(bytes)
            "ogg" -> decodeOgg(bytes)
            "mp3" -> decodeMp3(bytes)
            else -> {
                logger.error("Unsupported audio format: $extension")
                null
            }
        }
    }

    /**
     * Декодирует WAV через javax.sound.sampled (встроен в JVM).
     */
    private fun decodeWav(bytes: ByteArray): PcmData? {
        return try {
            val stream = AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes))
            val format = stream.format

            // Конвертируем в 16-bit PCM если нужно
            val targetFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                format.sampleRate,
                16,
                format.channels,
                format.channels * 2,
                format.sampleRate,
                false
            )
            val convertedStream = AudioSystem.getAudioInputStream(targetFormat, stream)
            val pcmBytes = convertedStream.readBytes()

            PcmData(
                data = pcmBytes,
                channels = format.channels,
                sampleRate = format.sampleRate.toInt()
            )
        } catch (e: Exception) {
            logger.error("Failed to decode WAV", e)
            null
        }
    }

    /**
     * Декодирует OGG через paulscode/lwjgl (встроен в Minecraft).
     */
    private fun decodeOgg(bytes: ByteArray): PcmData? {
        return try {
            // Minecraft использует STB Vorbis через LWJGL для декодирования OGG
            val buffer = org.lwjgl.BufferUtils.createByteBuffer(bytes.size)
            buffer.put(bytes)
            buffer.flip()

            val channelsBuffer = org.lwjgl.BufferUtils.createIntBuffer(1)
            val sampleRateBuffer = org.lwjgl.BufferUtils.createIntBuffer(1)

            val pcmBuffer = org.lwjgl.stb.STBVorbis.stb_vorbis_decode_memory(
                buffer, channelsBuffer, sampleRateBuffer
            ) ?: run {
                logger.error("STB Vorbis failed to decode OGG")
                return null
            }

            val channels = channelsBuffer.get(0)
            val sampleRate = sampleRateBuffer.get(0)

            // Конвертируем ShortBuffer в ByteArray
            val pcmBytes = ByteArray(pcmBuffer.remaining() * 2)
            val byteBuffer = java.nio.ByteBuffer.wrap(pcmBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            while (pcmBuffer.hasRemaining()) {
                byteBuffer.putShort(pcmBuffer.get())
            }

            org.lwjgl.stb.STBVorbis.stb_vorbis_decode_memory(buffer, channelsBuffer, sampleRateBuffer)

            PcmData(data = pcmBytes, channels = channels, sampleRate = sampleRate)
        } catch (e: Exception) {
            logger.error("Failed to decode OGG", e)
            null
        }
    }

    /**
     * Декодирует MP3 через JLayer (добавлен как зависимость).
     */
    private fun decodeMp3(bytes: ByteArray): PcmData? {
        return try {
            val stream = AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes))
            val baseFormat = stream.format

            // JLayer регистрирует себя как SPI провайдер — AudioSystem автоматически его использует
            val targetFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.sampleRate,
                16,
                baseFormat.channels,
                baseFormat.channels * 2,
                baseFormat.sampleRate,
                false
            )
            val pcmStream = AudioSystem.getAudioInputStream(targetFormat, stream)
            val pcmBytes = pcmStream.readBytes()

            PcmData(
                data = pcmBytes,
                channels = baseFormat.channels,
                sampleRate = baseFormat.sampleRate.toInt()
            )
        } catch (e: Exception) {
            logger.error("Failed to decode MP3", e)
            null
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────

    private fun cleanup() {
        if (sourceId != AL_NONE) {
            alDeleteSources(sourceId)
            sourceId = AL_NONE
        }
        if (bufferId != AL_NONE) {
            alDeleteBuffers(bufferId)
            bufferId = AL_NONE
        }
        currentTrackId = null
    }

    // ── Вспомогательные типы ──────────────────────────────────────────────

    private data class PcmData(
        val data: ByteArray,
        val channels: Int,
        val sampleRate: Int
    )
}


