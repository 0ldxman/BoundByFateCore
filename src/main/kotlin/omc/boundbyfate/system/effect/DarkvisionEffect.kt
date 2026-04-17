package omc.boundbyfate.system.effect

import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.api.effect.BbfEffect
import omc.boundbyfate.api.effect.BbfEffectContext
import omc.boundbyfate.component.DarkvisionData
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.registry.BbfAttachments

/**
 * Grants darkvision by applying vanilla Night Vision effect.
 * 
 * This uses Minecraft's built-in night vision which already works perfectly.
 * We just add our custom desaturation shader on top.
 */
class DarkvisionEffect(private val rangeFt: Int = 60) : BbfEffect {

    override fun apply(context: BbfEffectContext): Boolean {
        val player = context.source as? ServerPlayerEntity ?: return false

        // Store darkvision data for client shader
        player.setAttached(BbfAttachments.DARKVISION, DarkvisionData(rangeFt))

        // Apply vanilla Night Vision effect (infinite duration, no particles)
        val nightVision = StatusEffectInstance(
            StatusEffects.NIGHT_VISION,
            Int.MAX_VALUE, // Infinite duration
            0, // Amplifier 0
            false, // No ambient
            false, // No particles
            false  // No icon
        )
        player.addStatusEffect(nightVision)

        // Sync to client for desaturation shader
        BbfPackets.sendDarkvisionSync(player, rangeFt)

        return true
    }
}
