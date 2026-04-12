package omc.boundbyfate.system.feature.effect

import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import omc.boundbyfate.api.feature.FeatureContext
import omc.boundbyfate.api.feature.FeatureEffect

/**
 * Applies a vanilla Minecraft StatusEffect to each target.
 *
 * JSON params:
 * - effect: String (effect identifier, e.g. "minecraft:regeneration")
 * - duration: Int (duration in ticks, default 200)
 * - amplifier: Int (effect level 0-255, default 0)
 * - ambient: Boolean (reduces particles, default false)
 * - showParticles: Boolean (default true)
 * - showIcon: Boolean (default true)
 */
class ApplyMinecraftStatusEffect(
    private val effectId: Identifier,
    private val duration: Int = 200,
    private val amplifier: Int = 0,
    private val ambient: Boolean = false,
    private val showParticles: Boolean = true,
    private val showIcon: Boolean = true
) : FeatureEffect {

    override fun apply(context: FeatureContext) {
        val effect = Registries.STATUS_EFFECT.get(effectId) ?: return

        val instance = StatusEffectInstance(
            effect,
            duration,
            amplifier,
            ambient,
            showParticles,
            showIcon
        )

        for (target in context.targets) {
            target.addStatusEffect(instance)
        }
    }
}
