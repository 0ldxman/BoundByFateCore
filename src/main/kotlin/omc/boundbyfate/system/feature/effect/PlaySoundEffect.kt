package omc.boundbyfate.system.feature.effect

import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import net.minecraft.registry.Registries
import omc.boundbyfate.api.feature.FeatureContext
import omc.boundbyfate.api.feature.FeatureEffect

/**
 * Plays a sound at the caster's position.
 *
 * JSON params:
 * - sound: String (sound identifier, e.g. "minecraft:entity.player.levelup")
 * - volume: Float (default 1.0)
 * - pitch: Float (default 1.0)
 */
class PlaySoundEffect(
    private val soundId: Identifier,
    private val volume: Float = 1.0f,
    private val pitch: Float = 1.0f
) : FeatureEffect {

    override fun apply(context: FeatureContext) {
        val sound = Registries.SOUND_EVENT.get(soundId) ?: return
        val pos = context.caster.pos
        context.world.playSound(
            null,
            pos.x, pos.y, pos.z,
            sound,
            SoundCategory.PLAYERS,
            volume,
            pitch
        )
    }
}
