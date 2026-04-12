package omc.boundbyfate.client.network

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.particle.DefaultParticleType
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import omc.boundbyfate.client.state.ClientFeatureState
import omc.boundbyfate.network.BbfPackets

/**
 * Handles packets received from the server on the client side.
 */
object ClientPacketHandler {

    fun register() {
        // Server → Client: spawn particles
        ClientPlayNetworking.registerGlobalReceiver(BbfPackets.SPAWN_PARTICLES) { client, _, buf, _ ->
            val particleId = buf.readIdentifier()
            val x = buf.readDouble()
            val y = buf.readDouble()
            val z = buf.readDouble()
            val count = buf.readInt()
            val spread = buf.readFloat()
            val speed = buf.readFloat()

            client.execute {
                spawnParticles(client, particleId, x, y, z, count, spread, speed)
            }
        }

        // Server → Client: sync hotbar slots
        ClientPlayNetworking.registerGlobalReceiver(BbfPackets.SYNC_FEATURE_SLOTS) { client, _, buf, _ ->
            val slots = Array<Identifier?>(10) { null }
            for (i in 0..9) {
                val hasFeature = buf.readBoolean()
                slots[i] = if (hasFeature) buf.readIdentifier() else null
            }
            client.execute {
                for (i in 0..9) ClientFeatureState.setHotbarSlot(i, slots[i])
            }
        }

        // Server → Client: sync granted features
        ClientPlayNetworking.registerGlobalReceiver(BbfPackets.SYNC_GRANTED_FEATURES) { client, _, buf, _ ->
            val count = buf.readInt()
            val features = (0 until count).map { buf.readIdentifier() }.toSet()
            client.execute {
                ClientFeatureState.grantedFeatures.clear()
                ClientFeatureState.grantedFeatures.addAll(features)
            }
        }
    }

    private fun spawnParticles(
        client: MinecraftClient,
        particleId: Identifier,
        x: Double, y: Double, z: Double,
        count: Int,
        spread: Float,
        speed: Float
    ) {
        val world = client.world ?: return
        val particleType = Registries.PARTICLE_TYPE.get(particleId)
        if (particleType is DefaultParticleType) {
            repeat(count) {
                val offsetX = (Math.random() - 0.5) * spread
                val offsetY = (Math.random() - 0.5) * spread
                val offsetZ = (Math.random() - 0.5) * spread
                world.addParticle(particleType, x + offsetX, y + offsetY, z + offsetZ, 0.0, speed.toDouble(), 0.0)
            }
        }
    }
}
