package omc.boundbyfate.system.visual.builder

import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import omc.boundbyfate.api.visual.VisualContext
import omc.boundbyfate.system.visual.SoundSystem

/**
 * DSL билдер для подсистемы звуков.
 *
 * Используется внутри [VisualBuilder.sound].
 *
 * ```kotlin
 * sound("spell_cast") {
 *     at(pos, volume = 0.8f, pitch = 1.2f)
 * }
 * sound("ui_click") {
 *     toPlayer(player)
 * }
 * ```
 */
class SoundBuilder(
    private val soundId: Identifier,
    private val ctx: VisualContext
) {
    private val actions = mutableListOf<() -> Unit>()

    /**
     * Воспроизводит звук в точке мира (слышат все игроки в радиусе).
     */
    fun at(
        pos: Vec3d = ctx.pos,
        volume: Float = 1.0f,
        pitch: Float = 1.0f
    ) {
        actions += {
            SoundSystem.playAt(ctx.world, soundId, pos, SoundCategory.MASTER, volume, pitch)
        }
    }

    /**
     * Воспроизводит звук только конкретному игроку (UI звуки, личные эффекты).
     */
    fun toPlayer(
        player: ServerPlayerEntity,
        volume: Float = 1.0f,
        pitch: Float = 1.0f
    ) {
        actions += {
            SoundSystem.playGui(player, soundId, volume, pitch)
        }
    }

    /**
     * Воспроизводит звук всем игрокам на сервере.
     */
    fun toAll(volume: Float = 1.0f, pitch: Float = 1.0f) {
        actions += {
            val sound = Registries.SOUND_EVENT.get(soundId)
            if (sound != null) {
                SoundSystem.playGuiToAll(ctx.world.server, sound, volume, pitch)
            }
        }
    }

    internal fun execute() {
        actions.forEach { it() }
    }
}
