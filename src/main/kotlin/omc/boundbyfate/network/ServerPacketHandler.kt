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

        // ── Ability System ────────────────────────────────────────────────────

        // Client → Server: activate an ability
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.ACTIVATE_ABILITY) { server, player, _, buf, _ ->
            val abilityId = buf.readIdentifier()
            val hasTarget = buf.readBoolean()
            val targetId = if (hasTarget) buf.readInt() else null
            val hasTargetPos = buf.readBoolean()
            val targetPos = if (hasTargetPos) {
                net.minecraft.util.math.Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble())
            } else null
            val hasUpcast = buf.readBoolean()
            val upcastLevel = if (hasUpcast) buf.readInt() else null

            server.execute {
                val ability = omc.boundbyfate.registry.AbilityRegistry.get(abilityId)
                if (ability == null) {
                    logger.warn("Player ${player.name.string} tried to activate unknown ability $abilityId")
                    return@execute
                }

                // Resolve target entity
                val target = targetId?.let { id ->
                    player.serverWorld.getEntityById(id) as? net.minecraft.entity.LivingEntity
                }

                // Validate target distance
                if (target != null) {
                    val maxRange = getMaxRange(ability.targeting)
                    if (player.distanceTo(target) > maxRange) {
                        logger.debug("Target out of range for ${player.name.string}")
                        return@execute
                    }
                }

                // Validate target position distance
                if (targetPos != null) {
                    val maxRange = getMaxRange(ability.targeting)
                    if (player.pos.distanceTo(targetPos) > maxRange) {
                        logger.debug("Target position out of range for ${player.name.string}")
                        return@execute
                    }
                }

                // Activate ability
                val result = omc.boundbyfate.system.ability.AbilityActivationSystem.beginActivation(
                    player, ability, target, targetPos, upcastLevel
                )

                logger.debug("${player.name.string} activation result: $result")
            }
        }

        // Client → Server: release charged/channeled ability
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.RELEASE_ABILITY) { server, player, _, _, _ ->
            server.execute {
                val state = player.getAttachedOrElse(BbfAttachments.ABILITY_ACTIVATION, null)
                if (state == null) {
                    logger.debug("${player.name.string} tried to release ability but no activation state")
                    return@execute
                }

                val ability = omc.boundbyfate.registry.AbilityRegistry.get(state.abilityId)
                if (ability == null) {
                    logger.error("Unknown ability ${state.abilityId} in activation state")
                    return@execute
                }

                omc.boundbyfate.system.ability.AbilityActivationSystem.completeActivation(player, ability, state)
            }
        }

        // Client → Server: cancel ability activation
        ServerPlayNetworking.registerGlobalReceiver(BbfPackets.CANCEL_ABILITY) { server, player, _, _, _ ->
            server.execute {
                omc.boundbyfate.system.ability.AbilityActivationSystem.cancelActivation(player, "Player cancelled")
            }
        }
    }

    /**
     * Gets maximum range for a targeting component.
     */
    private fun getMaxRange(targeting: omc.boundbyfate.api.ability.component.TargetingComponent): Double {
        return targeting.range.toDouble()
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

    /**
     * Sends a custom skin to all online players.
     * Called when a player joins or when admin changes skin via command.
     *
     * @param targetName The player whose skin is being set
     * @param skinBase64 Base64-encoded PNG data
     * @param skinModel "default" or "slim"
     * @param server The Minecraft server instance
     */
    fun broadcastSkin(
        targetName: String,
        skinBase64: String,
        skinModel: String,
        server: net.minecraft.server.MinecraftServer
    ) {
        val buf = PacketByteBufs.create()
        buf.writeString(targetName)
        buf.writeString(skinModel)
        buf.writeString(skinBase64)

        server.playerManager.playerList.forEach { player ->
            ServerPlayNetworking.send(player, BbfPackets.SYNC_PLAYER_SKIN, buf)
        }
    }

    /**
     * Sends all currently active custom skins to a newly joined player.
     */
    fun syncAllSkinsToPlayer(
        player: net.minecraft.server.network.ServerPlayerEntity,
        server: net.minecraft.server.MinecraftServer
    ) {
        val worldDir = omc.boundbyfate.util.WorldDirUtil.getWorldDir(server)
        server.playerManager.playerList.forEach { online ->
            val skinData = online.getAttachedOrElse(BbfAttachments.PLAYER_SKIN, null) ?: return@forEach
            val base64 = omc.boundbyfate.system.skin.SkinLoader.loadAsBase64(worldDir, skinData.skinName) ?: return@forEach
            val buf = PacketByteBufs.create()
            buf.writeString(online.name.string)
            buf.writeString(skinData.skinModel)
            buf.writeString(base64)
            ServerPlayNetworking.send(player, BbfPackets.SYNC_PLAYER_SKIN, buf)
        }
    }

    /**
     * Broadcasts skin removal to all players.
     */
    fun broadcastSkinClear(
        targetName: String,
        server: net.minecraft.server.MinecraftServer
    ) {
        val buf = PacketByteBufs.create()
        buf.writeString(targetName)
        server.playerManager.playerList.forEach { player ->
            ServerPlayNetworking.send(player, BbfPackets.CLEAR_PLAYER_SKIN, buf)
        }
    }

    // ── Ability System ────────────────────────────────────────────────────────

    /**
     * Syncs ability activation state to client (for progress bar).
     */
    fun syncAbilityActivation(player: ServerPlayerEntity) {
        val state = player.getAttachedOrElse(BbfAttachments.ABILITY_ACTIVATION, null)
        val buf = PacketByteBufs.create()
        
        if (state != null) {
            buf.writeBoolean(true)
            buf.writeIdentifier(state.abilityId)
            buf.writeFloat(state.getProgress(player.world.time))
            buf.writeString(state.activationType.name)
        } else {
            buf.writeBoolean(false)
        }
        
        ServerPlayNetworking.send(player, BbfPackets.SYNC_ABILITY_ACTIVATION, buf)
    }

    /**
     * Updates concentration status.
     */
    fun updateConcentration(player: ServerPlayerEntity) {
        val concentration = player.getAttachedOrElse(BbfAttachments.CONCENTRATION, null)
        val buf = PacketByteBufs.create()
        
        if (concentration != null) {
            buf.writeBoolean(true)
            buf.writeIdentifier(concentration.abilityId)
        } else {
            buf.writeBoolean(false)
        }
        
        ServerPlayNetworking.send(player, BbfPackets.UPDATE_CONCENTRATION, buf)
    }

    /**
     * Broadcasts ability cast to all nearby players (for visual effects).
     */
    fun broadcastAbilityCast(
        caster: net.minecraft.entity.LivingEntity,
        abilityId: Identifier,
        world: net.minecraft.server.world.ServerWorld
    ) {
        val buf = PacketByteBufs.create()
        buf.writeInt(caster.id)
        buf.writeIdentifier(abilityId)
        
        // Send to all players within 64 blocks
        world.players.forEach { player ->
            if (player.distanceTo(caster) <= 64.0) {
                ServerPlayNetworking.send(player, BbfPackets.BROADCAST_ABILITY_CAST, buf)
            }
        }
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
