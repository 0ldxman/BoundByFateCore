package omc.boundbyfate.system.visual

import net.minecraft.registry.Registries
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import omc.boundbyfate.network.core.PacketSender
import omc.boundbyfate.network.extension.broadcastPacketInRadius
import omc.boundbyfate.network.extension.sendPacket
import omc.boundbyfate.network.packet.s2c.PlaySoundPacket
import org.slf4j.LoggerFactory

/**
 * Серверная система звуков.
 *
 * Работает только с зарегистрированными [SoundEvent] — ванильными или из мода.
 * Для кастомной музыки от ГМ используется [omc.boundbyfate.system.visual.sound.MusicSystem].
 *
 * ## Типы звуков
 *
 * **Environment** — позиционный звук в мире, слышат игроки в радиусе:
 * ```kotlin
 * SoundSystem.playAt(world, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, pos)
 * ```
 *
 * **GUI** — не позиционный звук конкретному игроку (UI клики, уведомления):
 * ```kotlin
 * SoundSystem.playGui(player, SoundEvents.UI_BUTTON_CLICK)
 * ```
 */
object SoundSystem {

    private val logger = LoggerFactory.getLogger(SoundSystem::class.java)

    /** Радиус по умолчанию для позиционных звуков. */
    const val DEFAULT_RADIUS = 64.0

    // ── Environment звуки ─────────────────────────────────────────────────

    /**
     * Воспроизводит позиционный звук в точке мира.
     * Слышат все игроки в радиусе [radius].
     *
     * ```kotlin
     * SoundSystem.playAt(world, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, pos)
     * ```
     */
    fun playAt(
        world: ServerWorld,
        sound: SoundEvent,
        pos: Vec3d,
        category: SoundCategory = SoundCategory.MASTER,
        volume: Float = 1.0f,
        pitch: Float = 1.0f,
        radius: Double = DEFAULT_RADIUS
    ) {
        val packet = PlaySoundPacket(
            sound = RegistryEntry.of(sound),
            category = category,
            x = pos.x, y = pos.y, z = pos.z,
            volume = volume,
            pitch = pitch,
            positional = true
        )
        world.broadcastPacketInRadius(pos.x, pos.y, pos.z, radius, packet)
    }

    /**
     * Воспроизводит позиционный звук по Identifier.
     * Удобно когда нет прямой ссылки на SoundEvent.
     */
    fun playAt(
        world: ServerWorld,
        soundId: Identifier,
        pos: Vec3d,
        category: SoundCategory = SoundCategory.MASTER,
        volume: Float = 1.0f,
        pitch: Float = 1.0f,
        radius: Double = DEFAULT_RADIUS
    ) {
        val sound = Registries.SOUND_EVENT.get(soundId)
        if (sound == null) {
            logger.warn("Sound not found: $soundId")
            return
        }
        playAt(world, sound, pos, category, volume, pitch, radius)
    }

    // ── GUI звуки ─────────────────────────────────────────────────────────

    /**
     * Воспроизводит не позиционный звук конкретному игроку.
     * Используется для UI кликов, уведомлений и т.д.
     *
     * ```kotlin
     * SoundSystem.playGui(player, SoundEvents.UI_BUTTON_CLICK)
     * ```
     */
    fun playGui(
        player: ServerPlayerEntity,
        sound: SoundEvent,
        volume: Float = 1.0f,
        pitch: Float = 1.0f
    ) {
        val packet = PlaySoundPacket(
            sound = RegistryEntry.of(sound),
            category = SoundCategory.MASTER,
            x = player.x, y = player.y, z = player.z,
            volume = volume,
            pitch = pitch,
            positional = false
        )
        player.sendPacket(packet)
    }

    /**
     * Воспроизводит не позиционный звук конкретному игроку по Identifier.
     */
    fun playGui(
        player: ServerPlayerEntity,
        soundId: Identifier,
        volume: Float = 1.0f,
        pitch: Float = 1.0f
    ) {
        val sound = Registries.SOUND_EVENT.get(soundId)
        if (sound == null) {
            logger.warn("Sound not found: $soundId")
            return
        }
        playGui(player, sound, volume, pitch)
    }

    /**
     * Воспроизводит не позиционный звук всем игрокам на сервере.
     */
    fun playGuiToAll(
        server: MinecraftServer,
        sound: SoundEvent,
        volume: Float = 1.0f,
        pitch: Float = 1.0f
    ) {
        server.playerManager.playerList.forEach { playGui(it, sound, volume, pitch) }
    }
}
