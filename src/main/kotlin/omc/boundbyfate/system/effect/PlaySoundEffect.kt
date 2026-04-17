package omc.boundbyfate.system.effect

import net.minecraft.registry.Registries
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import omc.boundbyfate.api.effect.BbfEffect
import omc.boundbyfate.api.effect.BbfEffectContext

/**
 * Plays a sound at the source entity's position.
 *
 * JSON params:
 * - sound: String (e.g. "minecraft:entity.player.levelup")
 * - volume: Float (default 1.0)
 * - pitch: Float (default 1.0)
 */
class PlaySoundEffect(
    private val soundId: Identifier,
    private val volume: Float = 1.0f,
    private val pitch: Float = 1.0f
) : BbfEffect {

    override fun apply(context: BbfEffectContext): Boolean {
        val sound = Registries.SOUND_EVENT.get(soundId) ?: return false
        val pos = context.source.pos
        context.world.playSound(null, pos.x, pos.y, pos.z, sound, SoundCategory.PLAYERS, volume, pitch)
        return true
    }
}
