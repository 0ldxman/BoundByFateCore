package omc.boundbyfate.system.effect

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.util.Identifier
import omc.boundbyfate.api.effect.BbfEffect
import omc.boundbyfate.api.effect.BbfEffectContext
import omc.boundbyfate.network.BbfPackets

/**
 * Sends a particle spawn packet to nearby clients.
 *
 * JSON params:
 * - particle: String (e.g. "minecraft:heart")
 * - count: Int (default 10)
 * - spread: Float (default 0.5)
 * - speed: Float (default 0.1)
 * - onSource: Boolean (spawn on source entity, default true)
 * - onTargets: Boolean (spawn on each target, default false)
 */
class SpawnParticlesEffect(
    private val particleId: Identifier,
    private val count: Int = 10,
    private val spread: Float = 0.5f,
    private val speed: Float = 0.1f,
    private val onSource: Boolean = true,
    private val onTargets: Boolean = false
) : BbfEffect {

    override fun apply(context: BbfEffectContext): Boolean {
        val positions = mutableListOf<Triple<Double, Double, Double>>()

        if (onSource) {
            val pos = context.source.pos
            positions.add(Triple(pos.x, pos.y + context.source.height / 2, pos.z))
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
            buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z)
            buf.writeInt(count); buf.writeFloat(spread); buf.writeFloat(speed)
            PlayerLookup.around(context.world, context.source.pos, 64.0)
                .forEach { player -> ServerPlayNetworking.send(player, BbfPackets.SPAWN_PARTICLES, buf) }
        }
        return true
    }
}
