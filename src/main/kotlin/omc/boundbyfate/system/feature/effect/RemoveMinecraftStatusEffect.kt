package omc.boundbyfate.system.feature.effect

import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import omc.boundbyfate.api.feature.FeatureContext
import omc.boundbyfate.api.feature.FeatureEffect

/**
 * Removes a vanilla Minecraft StatusEffect from each target.
 *
 * JSON params:
 * - effect: String (effect identifier, e.g. "minecraft:poison")
 */
class RemoveMinecraftStatusEffect(
    private val effectId: Identifier
) : FeatureEffect {

    override fun apply(context: FeatureContext) {
        val effect = Registries.STATUS_EFFECT.get(effectId) ?: return
        for (target in context.targets) {
            target.removeStatusEffect(effect)
        }
    }
}
