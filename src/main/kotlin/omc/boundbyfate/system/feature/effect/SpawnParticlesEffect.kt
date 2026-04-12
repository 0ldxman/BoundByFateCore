package omc.boundbyfate.system.feature.effect

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.api.feature.FeatureContext
import omc.boundbyfate.api.feature.FeatureEffect
import omc.boundbyfate.network.BbfPackets

/**
 * Sends a particle spawn packet to nearby clients.
 *
 * JSON params:
 * - particle: String (particle identifier, e.g. "minecraft:heart")
 * - count: Int (default 10)
 * - spread: Float (spread radius, default 0.5)
 * - speed: Float (particle speed, default 0.1)
 * - onCaster: Boolean (spawn on caster, default true)
 * - onTargets: Boolean (spawn on each target, default false)
 */
class SpawnParticlesEffect(
    private val particleId: Identifier,
    private val count: Int = 10,
    private val spread: Float = 0.5f,
    private val speed: Float = 0.1f,
    private val onCaster: Boolean = true,
    private val onTargets: Boolean = false
) : FeatureEffect {

    override fun apply(context: FeatureContext) {
        val positions = mutableListOf<Triple<Double, Double, Double>>()

        if (onCaster) {
            val pos = context.caster.pos
            positions.add(Triple(pos.x, pos.y + context.caster.height / 2, pos.z))
        }

        if (onTargets) {
            for (target in context.targets) {
                val pos = target.pos
                positions.add(Triple(pos.x, pos.y + target.height / 2, pos.z))
            }
        }

        for ((x, y, z) in positions) {
            val buf = PacketByteBufs.create()
            buf.writeIdentifier(particleId)
            buf.writeDouble(x)
            buf.writeDouble(y)
            buf.writeDouble(z)
            buf.writeInt(count)
            buf.writeFloat(spread)
            buf.writeFloat(speed)

            // Send to all players within 64 blocks
            PlayerLookup.around(context.world, context.caster.blockPos, 64)
                .forEach { player ->
                    ServerPlayNetworking.send(player, BbfPackets.SPAWN_PARTICLES, buf)
                }
        }
    }
}
