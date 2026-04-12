package omc.boundbyfate.network

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.component.EntityFeatureData
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.WeaponRegistry
import omc.boundbyfate.system.feature.FeatureSystem
import org.slf4j.LoggerFactory

/**
 * Handles packets received from clients on the server side.
 * Also provides methods to send packets to clients.
 */
object ServerPacketHandler {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    fun register() {
        // Client → Server: player activates a feature via keybind
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.USE_FEATURE) { server, player, _, buf, _ ->
            val featureId = buf.readIdentifier()
            val hasTarget = buf.readBoolean()
            val targetUuid = if (hasTarget) buf.readUuid() else null

            server.execute {
                val target = targetUuid?.let { uuid ->
                    player.serverWorld.getEntitiesByClass(
                        net.minecraft.entity.LivingEntity::class.java,
                        player.boundingBox.expand(20.0)
                    ) { it.uuid == uuid }.firstOrNull()
                }
                FeatureSystem.execute(player, featureId, target)
            }
        }

        // Client → Server: player updated a hotbar slot
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.UPDATE_FEATURE_SLOT) { server, player, _, buf, _ ->
            val slot = buf.readInt()
            val hasFeature = buf.readBoolean()
            val featureId = if (hasFeature) buf.readIdentifier() else null

            server.execute {
                if (slot !in 0..9) return@execute
                val data = player.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, EntityFeatureData())
                player.setAttached(BbfAttachments.ENTITY_FEATURES, data.withHotbarSlot(slot, featureId))
                logger.debug("Player ${player.name.string} set slot $slot to $featureId")
            }
        }
    }

    /**
     * Sends current hotbar slots and granted features to a player.
     * Call on player join.
     */
    fun syncToClient(player: ServerPlayerEntity) {
        val data = player.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, EntityFeatureData())

        // Sync hotbar slots
        val slotsBuf = PacketByteBufs.create()
        for (i in 0..9) {
            val featureId = data.getHotbarSlot(i)
            slotsBuf.writeBoolean(featureId != null)
            if (featureId != null) slotsBuf.writeIdentifier(featureId)
        }
        ServerPlayNetworking.send(player, BbfPackets.SYNC_FEATURE_SLOTS, slotsBuf)

        // Sync granted features
        val featuresBuf = PacketByteBufs.create()
        featuresBuf.writeInt(data.grantedFeatures.size)
        data.grantedFeatures.forEach { featuresBuf.writeIdentifier(it) }
        ServerPlayNetworking.send(player, BbfPackets.SYNC_GRANTED_FEATURES, featuresBuf)

        // Sync weapon registry for client-side tooltips
        syncWeaponRegistry(player)
    }

    /**
     * Sends floating attack roll text to the attacker only.
     */
    fun sendAttackRoll(
        attacker: ServerPlayerEntity,
        targetX: Double, targetY: Double, targetZ: Double,
        roll: Int, bonus: Int, hit: Boolean, isCrit: Boolean
    ) {
        val buf = PacketByteBufs.create()
        buf.writeDouble(targetX)
        buf.writeDouble(targetY)
        buf.writeDouble(targetZ)
        buf.writeInt(roll)
        buf.writeInt(bonus)
        buf.writeBoolean(hit)
        buf.writeBoolean(isCrit)
        ServerPlayNetworking.send(attacker, BbfPackets.SHOW_ATTACK_ROLL, buf)
    }

    private fun syncWeaponRegistry(player: ServerPlayerEntity) {
        val weapons = WeaponRegistry.getAll()
        val buf = PacketByteBufs.create()
        buf.writeInt(weapons.size)
        weapons.forEach { def ->
            buf.writeIdentifier(def.id)
            buf.writeString(def.displayName)
            buf.writeInt(def.items.size)
            def.items.forEach { buf.writeIdentifier(it) }
            buf.writeString(def.damage)
            buf.writeBoolean(def.versatileDamage != null)
            def.versatileDamage?.let { buf.writeString(it) }
            buf.writeIdentifier(def.damageType)
            buf.writeInt(def.properties.size)
            def.properties.forEach { buf.writeString(it.name) }
        }
        ServerPlayNetworking.send(player, BbfPackets.SYNC_WEAPON_REGISTRY, buf)
    }
}
